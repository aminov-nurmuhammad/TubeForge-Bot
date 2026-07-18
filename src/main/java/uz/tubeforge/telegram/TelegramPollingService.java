package uz.tubeforge.telegram;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.tubeforge.config.TelegramProperties;
import uz.tubeforge.telegram.model.TgUpdate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TelegramPollingService {
    private static final Logger log = LoggerFactory.getLogger(TelegramPollingService.class);

    private final TelegramProperties properties;
    private final TelegramApiClient telegram;
    private final TelegramUpdateRouter router;
    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile long nextOffset;

    public TelegramPollingService(TelegramProperties properties, TelegramApiClient telegram,
                                  TelegramUpdateRouter router) {
        this.properties = properties;
        this.telegram = telegram;
        this.router = router;
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
        if (!enabled() || !polling.compareAndSet(false, true)) return;
        try {
            List<TgUpdate> updates = telegram.getUpdates(nextOffset);
            for (TgUpdate update : updates) {
                nextOffset = Math.max(nextOffset, update.updateId() + 1);
                router.handle(update);
            }
        } catch (TelegramApiException e) {
            log.warn("Telegram polling error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected polling error", e);
        } finally {
            polling.set(false);
        }
    }

    private boolean enabled() {
        return properties.pollingEnabled() && properties.configured();
    }
}
