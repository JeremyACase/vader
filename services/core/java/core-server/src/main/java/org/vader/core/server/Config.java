package org.vader.core.server;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Central Spring configuration for the Vader Core Server.
 *
 * <p>Ensures the JVM's default timezone is set to UTC on startup, so that all timestamp
 * handling within the service is consistent regardless of the host's local timezone.</p>
 */
@Configuration
public class Config {

    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    /**
     * Sets the default JVM timezone to UTC after bean construction.
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        logger.info("Default timezone set to: {}", TimeZone.getDefault().getID());
    }
}
