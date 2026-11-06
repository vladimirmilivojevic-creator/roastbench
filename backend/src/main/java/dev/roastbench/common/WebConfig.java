package dev.roastbench.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    public WebConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = properties.corsOrigins();
        if (origins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Bean
    OpenAPI roastBenchOpenApi() {
        return new OpenAPI().info(new Info()
                .title("RoastBench API")
                .version("1.0")
                .description("""
                        Roast profile and green coffee inventory API.

                        Import roast logs (CSV and Artisan .alog), calculate rate of rise, development time ratio and \
                        roast loss, compare a batch with a reference profile, and manage green coffee lots with an \
                        append-only stock ledger and low-stock alerts. Demo data is synthetic.""")
                .contact(new Contact().name("Vladimir Milivojevic")
                        .url("https://github.com/vladimirmilivojevic-creator/roastbench"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}
