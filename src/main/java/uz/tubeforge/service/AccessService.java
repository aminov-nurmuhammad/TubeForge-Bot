package uz.tubeforge.service;

import org.springframework.stereotype.Service;
import uz.tubeforge.config.AccessProperties;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.repository.DownloadJobRepository;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Service
public class AccessService {
    private final AccessProperties properties;
    private final DownloadJobRepository jobs;
    private final Clock clock;

    public AccessService(AccessProperties properties, DownloadJobRepository jobs, Clock clock) {
        this.properties = properties;
        this.jobs = jobs;
        this.clock = clock;
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
}
