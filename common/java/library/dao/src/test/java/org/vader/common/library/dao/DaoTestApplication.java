package org.vader.common.library.dao;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Boots an H2-backed context with the DAO components and the vader entities for slice tests.
 */
@SpringBootApplication
@ComponentScan("org.vader.common.library.dao")
@EntityScan("org.vader.common.model.vader.entity")
@EnableJpaRepositories("org.vader.common.library.dao")
public class DaoTestApplication {
}
