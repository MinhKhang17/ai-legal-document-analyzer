package com.analyzer.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kept out of {@link com.analyzer.api.LegalAnalyzerApplication} so that @WebMvcTest slices
 * (which don't load the JPA auto-configuration) don't fail trying to create the
 * jpaAuditingHandler bean.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
