package com.github.gotson.spring.session.caffeine.config.annotation.web.http;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.session.MapSession;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.SessionRepositoryFilter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Add this annotation to an {@code @Configuration} class to expose the
 * {@link SessionRepositoryFilter} as a bean named {@code springSessionRepositoryFilter}
 * and backed by Caffeine.
 * <p>
 * More advanced configurations can extend {@link CaffeineHttpSessionConfiguration}
 * instead.
 *
 * @see EnableSpringHttpSession
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(CaffeineHttpSessionConfiguration.class)
@Configuration(proxyBeanMethods = false)
public @interface EnableCaffeineHttpSession {

    /**
     * The session timeout in seconds. By default, it is set to 1800 seconds (30 minutes).
     * This should be a non-negative integer.
     *
     * @return the seconds a session can be inactive before expiring
     */
    int maxInactiveIntervalInSeconds() default MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;
}
