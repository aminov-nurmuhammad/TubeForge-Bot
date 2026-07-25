package uz.tubeforge.telegram;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.tubeforge.config.TelegramProperties;
import uz.tubeforge.service.PerformanceMetrics;
import uz.tubeforge.telegram.model.TgUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routes independent chats concurrently while preserving Telegram's update order inside each chat.
 * A bounded stripe queue prevents an update storm from exhausting the JVM heap.
 */
@Component
public class TelegramUpdateDispatcher {
    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateDispatcher.class);

    private final TelegramUpdateRouter router;
    private final PerformanceMetrics metrics;
    private final List<ThreadPoolExecutor> stripes;
    private final AtomicInteger threadSequence = new AtomicInteger();

    public TelegramUpdateDispatcher(TelegramProperties properties, TelegramUpdateRouter router,
                                    PerformanceMetrics metrics) {
        this.router = router;
        this.metrics = metrics;
        int stripeCount = properties.maxConcurrentUpdates();
        int queuePerStripe = Math.max(10,
                (int) Math.ceil((double) properties.maxQueuedUpdates() / stripeCount));
        this.stripes = new ArrayList<>(stripeCount);
        for (int index = 0; index < stripeCount; index++) {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    1, 1, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(queuePerStripe),
                    task -> {
                        Thread thread = new Thread(task,
                                "telegram-update-" + threadSequence.incrementAndGet());
                        thread.setDaemon(false);
                        return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy());
            executor.prestartCoreThread();
            stripes.add(executor);
        }
    }

    public CompletableFuture<Void> dispatch(TgUpdate update) {
        ThreadPoolExecutor stripe = stripes.get(Math.floorMod(orderingKey(update), stripes.size()));
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            stripe.execute(() -> {
                try {
                    metrics.dispatchedUpdate();
                    router.handle(update);
                    completion.complete(null);
                } catch (Throwable error) {
                    completion.completeExceptionally(error);
                }
            });
            return completion;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            metrics.rejectedUpdate();
            log.warn("Telegram update queue is full; update {} will be requested again", update.updateId());
            return null;
        }
    }

    public int queuedUpdates() {
        return stripes.stream().mapToInt(executor -> executor.getQueue().size()).sum();
    }

    public int activeWorkers() {
        return stripes.stream().mapToInt(ThreadPoolExecutor::getActiveCount).sum();
    }

    static int orderingKey(TgUpdate update) {
        if (update.message() != null && update.message().chat() != null) {
            return Long.hashCode(update.message().chat().id());
        }
        if (update.callbackQuery() != null) {
            if (update.callbackQuery().message() != null && update.callbackQuery().message().chat() != null) {
                return Long.hashCode(update.callbackQuery().message().chat().id());
            }
            if (update.callbackQuery().from() != null) {
                return Long.hashCode(update.callbackQuery().from().id());
            }
        }
        return Long.hashCode(update.updateId());
    }

    @PreDestroy
    void shutdown() {
        stripes.forEach(ThreadPoolExecutor::shutdown);
        for (ThreadPoolExecutor executor : stripes) {
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }
}
