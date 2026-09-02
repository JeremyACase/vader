package org.vader.core.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * Entry point for the Vader Core Server.
 *
 * <p>Datasource autoconfiguration is left enabled: the persistence backend (H2,
 * see {@code vader.database.type} in Helm values) is wired up via {@code application.yaml}
 * rather than excluded here.</p>
 */
@SpringBootApplication(scanBasePackages = {"org.vader.core.server", "org.vader.common.library"})
@EntityScan("org.vader.common.model.vader.entity")
public class Application {

    /**
     * Launches the Spring Boot application.
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
