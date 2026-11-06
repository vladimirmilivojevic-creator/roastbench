package dev.roastbench.common;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings under the {@code roastbench} prefix.
 *
 * @param corsAllowedOrigins comma separated browser origins allowed to call the API
 * @param adminToken         token for the demo reset endpoint; blank disables it
 * @param limits             caps that keep the public demo small
 * @param demo               demo data settings
 */
@ConfigurationProperties(prefix = "roastbench")
public record AppProperties(String corsAllowedOrigins, String adminToken, Limits limits, Demo demo) {

    public record Limits(int maxRoasts, int maxLots) {
    }

    public record Demo(boolean seedOnStartup) {
    }

    public List<String> corsOrigins() {
        if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(corsAllowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
