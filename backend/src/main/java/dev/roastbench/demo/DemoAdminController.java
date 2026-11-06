package dev.roastbench.demo;

import dev.roastbench.common.AppProperties;
import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Resets the public demo to its seed data. Disabled unless an admin token is configured. */
@RestController
@RequestMapping("/api/admin/demo")
@Hidden
public class DemoAdminController {

    private final DemoDataService demo;
    private final AppProperties properties;

    public DemoAdminController(DemoDataService demo, AppProperties properties) {
        this.demo = demo;
        this.properties = properties;
    }

    @PostMapping("/reset")
    public Map<String, String> reset(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        String expected = properties.adminToken();
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (token == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin token");
        }
        demo.reset();
        return Map.of("status", "reset");
    }
}
