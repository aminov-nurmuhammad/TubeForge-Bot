package uz.tubeforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties("tubeforge.access")
public record AccessProperties(
        AccessMode mode,
        Set<Long> adminUserIds,
        Set<Long> allowedUserIds,
        int dailyJobLimit,
        boolean requireTermsAcceptance
) {
    public AccessProperties {
        adminUserIds = adminUserIds == null ? Set.of() : Set.copyOf(adminUserIds);
        allowedUserIds = allowedUserIds == null ? Set.of() : Set.copyOf(allowedUserIds);
        mode = mode == null ? AccessMode.PUBLIC : mode;
    }

    public enum AccessMode {
        PUBLIC,
        ALLOWLIST
    }
}
