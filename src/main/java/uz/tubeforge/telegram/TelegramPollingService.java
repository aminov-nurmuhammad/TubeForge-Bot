package uz.tubeforge.telegram;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.tubeforge.config.TelegramProperties;
import uz.tubeforge.telegram.model.TgUpdate;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TelegramPollingService {
    private static final Logger log = LoggerFactory.getLogger(TelegramPollingService.class);

    private final TelegramProperties properties;
    private final TelegramApiClient telegram;
    private final TelegramUpdateDispatcher dispatcher;
    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile long nextOffset;
    private volatile long backoffUntilEpochMillis;
    private volatile int consecutiveFailures;

    public TelegramPollingService(TelegramProperties properties, TelegramApiClient telegram,
                                  TelegramUpdateDispatcher dispatcher) {
        this.properties = properties;
        this.telegram = telegram;
        this.dispatcher = dispatcher;
    }

    @PostConstruct
    void configureCommands() {
        if (!enabled()) {
            log.warn("Telegram polling is disabled or TELEGRAM_BOT_TOKEN is missing");
            return;
        }
        try {
            telegram.setCommands();
        } catch (Exception e) {
            log.warn("Could not configure Telegram commands yet: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 500)
    public void poll() {
        if (!enabled() || System.currentTimeMillis() < backoffUntilEpochMillis
                || !polling.compareAndSet(false, true)) return;
        try {
            List<TgUpdate> updates = telegram.getUpdates(nextOffset);
            consecutiveFailures = 0;
            backoffUntilEpochMillis = 0;
            List<CompletableFuture<Void>> accepted = new ArrayList<>(updates.size());
            long acceptedOffset = nextOffset;
            for (TgUpdate update : updates) {
                CompletableFuture<Void> completion = dispatcher.dispatch(update);
                if (completion == null) break;
                accepted.add(completion);
                acceptedOffset = Math.max(acceptedOffset, update.updateId() + 1);
            }
            if (!accepted.isEmpty()) {
                // Do not confirm Telegram offsets until every accepted update in this batch
                // has actually been routed. This keeps the fast concurrent path crash-safe.
                CompletableFuture.allOf(accepted.toArray(CompletableFuture[]::new)).join();
                nextOffset = acceptedOffset;
            }
        } catch (TelegramApiException e) {
            log.warn("Telegram polling error: {}", e.getMessage());
            scheduleBackoff(e);
        } catch (Exception e) {
            log.error("Unexpected polling error", e);
            scheduleBackoff(null);
        } finally {
            polling.set(false);
        }
    }

    private boolean enabled() {
        return properties.pollingEnabled() && properties.configured();
    }

    private void scheduleBackoff(TelegramApiException error) {
        consecutiveFailures = Math.min(10, consecutiveFailures + 1);
        long exponentialSeconds = Math.min(60, 1L << Math.min(6, consecutiveFailures - 1));
        long requestedSeconds = error == null ? 0 : error.getRetryAfterSeconds();
        long conflictSeconds = error != null && error.getErrorCode() == 409 ? 15 : 0;
        long delaySeconds = Math.max(exponentialSeconds, Math.max(requestedSeconds, conflictSeconds));
        backoffUntilEpochMillis = System.currentTimeMillis() + delaySeconds * 1000;
    }
}
