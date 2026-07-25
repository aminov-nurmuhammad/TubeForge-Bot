package uz.tubeforge.service;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import uz.tubeforge.config.AccessProperties;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.repository.DownloadJobRepository;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccessService {
    private final AccessProperties properties;
    private final DownloadJobRepository jobs;
    private final Clock clock;
    private final PerformanceMetrics metrics;
    private final ConcurrentHashMap<Long, LinkWindow> linkWindows = new ConcurrentHashMap<>();

    public AccessService(AccessProperties properties, DownloadJobRepository jobs, Clock clock,
                         PerformanceMetrics metrics) {
        this.properties = properties;
        this.jobs = jobs;
        this.clock = clock;
        this.metrics = metrics;
    }

    public boolean isAdmin(long userId) {
        return properties.adminUserIds().contains(userId);
    }

    public boolean isAllowed(long userId) {
        return isAdmin(userId)
                || properties.mode() == AccessProperties.AccessMode.PUBLIC
                || properties.allowedUserIds().contains(userId);
    }

    public boolean hasAcceptedTerms(AppUser user) {
        return !properties.requireTermsAcceptance() || user.getTermsAcceptedAt() != null;
    }

    public int jobsRemaining(long userId) {
        if (isAdmin(userId)) return Integer.MAX_VALUE;
        long used = jobs.countByTelegramUserIdAndCreatedAtAfter(userId,
                clock.instant().minus(24, ChronoUnit.HOURS));
        return Math.max(0, properties.dailyJobLimit() - (int) used);
    }

    public boolean canCreateJob(long userId) {
        return jobsRemaining(userId) > 0;
    }

    public boolean canInspectLink(long userId) {
        if (isAdmin(userId)) return true;
        long minute = clock.instant().getEpochSecond() / 60;
        LinkWindow window = linkWindows.compute(userId, (ignored, current) ->
                current == null || current.minute() != minute
                        ? new LinkWindow(minute, 1)
                        : new LinkWindow(minute, current.count() + 1));
        boolean allowed = window.count() <= properties.maxLinksPerMinute();
        if (!allowed) metrics.rateLimitedLink();
        return allowed;
    }

    @Scheduled(fixedDelayString = "PT5M")
    void clearExpiredLinkWindows() {
        long currentMinute = clock.instant().getEpochSecond() / 60;
        linkWindows.entrySet().removeIf(entry -> entry.getValue().minute() < currentMinute - 1);
    }

    private record LinkWindow(long minute, int count) {}
}
