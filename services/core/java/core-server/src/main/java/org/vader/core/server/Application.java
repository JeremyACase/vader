package org.vader.core.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Vader Core Server.
 *
 * <p>Datasource autoconfiguration is left enabled: the persistence backend (HSQL by default,
 * see {@code vader.database.type} in Helm values) is wired up via {@code application.yaml}
 * rather than excluded here.</p>
 */
@SpringBootApplication
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
