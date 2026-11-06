package dev.roastbench.demo;

import dev.roastbench.common.AppProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Seeds an empty database at startup when {@code roastbench.demo.seed-on-startup} is on. */
@Component
class DemoStartup implements ApplicationRunner {

    private final DemoDataService demo;
    private final AppProperties properties;

    DemoStartup(DemoDataService demo, AppProperties properties) {
        this.demo = demo;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.demo().seedOnStartup()) {
            demo.seedIfEmpty();
        }
    }
}
