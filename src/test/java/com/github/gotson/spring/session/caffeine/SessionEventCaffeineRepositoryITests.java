package com.github.gotson.spring.session.caffeine;

import com.github.benmanes.caffeine.cache.Scheduler;
import com.github.gotson.spring.session.caffeine.config.annotation.web.http.EnableCaffeineHttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
public class SessionEventCaffeineRepositoryITests<S extends Session> {

    private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 2;

    @Configuration
    @EnableCaffeineHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
    static class CaffeineSessionConfig {
        @Bean
        SessionEventRegistry sessionEventRegistry() {
            return new SessionEventRegistry();
        }

        @Bean
        SessionRepositoryCustomizer<CaffeineIndexedSessionRepository> customize() {
            return (sessionRepository -> {
                sessionRepository.setExecutor(Executors.newFixedThreadPool(10));
                sessionRepository.setScheduler(Scheduler.forScheduledExecutorService(Executors.newScheduledThreadPool(10)));
            }
            );
        }
    }

    @Autowired
    private SessionRepository<S> repository;

    @Autowired
    private SessionEventRegistry registry;

    @BeforeEach
    void setup() {
        this.registry.clear();
    }

    @Test
    void saveSessionTest() throws InterruptedException {
        String username = "saves-" + System.currentTimeMillis();

        S sessionToSave = this.repository.createSession();

        String expectedAttributeName = "a";
        String expectedAttributeValue = "b";
        sessionToSave.setAttribute(expectedAttributeName, expectedAttributeValue);
        Authentication toSaveToken = new UsernamePasswordAuthenticationToken(username, "password",
            AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContext toSaveContext = SecurityContextHolder.createEmptyContext();
        toSaveContext.setAuthentication(toSaveToken);
        sessionToSave.setAttribute("SPRING_SECURITY_CONTEXT", toSaveContext);
        sessionToSave.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);

        this.repository.save(sessionToSave);

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
            .isInstanceOf(SessionCreatedEvent.class);

        Session session = this.repository.findById(sessionToSave.getId());

        assertThat(session.getId()).isEqualTo(sessionToSave.getId());
        assertThat(session.getAttributeNames()).isEqualTo(sessionToSave.getAttributeNames());
        assertThat(session.<String>getAttribute(expectedAttributeName))
            .isEqualTo(sessionToSave.getAttribute(expectedAttributeName));
    }

    @Test
    void expiredSessionTest() throws InterruptedException {
        S sessionToSave = this.repository.createSession();

        this.repository.save(sessionToSave);

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
            .isInstanceOf(SessionCreatedEvent.class);
        this.registry.clear();

        assertThat(sessionToSave.getMaxInactiveInterval())
            .isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionExpiredEvent>getEvent(sessionToSave.getId()))
            .isInstanceOf(SessionExpiredEvent.class);

        assertThat(this.repository.findById(sessionToSave.getId())).isNull();
    }

    @Test
    void deletedSessionTest() throws InterruptedException {
        S sessionToSave = this.repository.createSession();

        this.repository.save(sessionToSave);

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
            .isInstanceOf(SessionCreatedEvent.class);
        this.registry.clear();

        this.repository.deleteById(sessionToSave.getId());

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionDeletedEvent>getEvent(sessionToSave.getId()))
            .isInstanceOf(SessionDeletedEvent.class);

        assertThat(this.repository.findById(sessionToSave.getId())).isNull();
    }

    @Test
    void saveUpdatesTimeToLiveTest() throws InterruptedException {
        S sessionToSave = this.repository.createSession();
        sessionToSave.setMaxInactiveInterval(Duration.ofSeconds(3));
        this.repository.save(sessionToSave);

        Thread.sleep(2000);

        // Get and save the session like SessionRepositoryFilter would.
        S sessionToUpdate = this.repository.findById(sessionToSave.getId());
        sessionToUpdate.setLastAccessedTime(Instant.now());
        this.repository.save(sessionToUpdate);

        Thread.sleep(2000);

        assertThat(this.repository.findById(sessionToUpdate.getId())).isNotNull();
    }

    @Test
    void changeSessionIdNoEventTest() throws InterruptedException {
        S sessionToSave = this.repository.createSession();
        sessionToSave.setMaxInactiveInterval(Duration.ofMinutes(30));

        this.repository.save(sessionToSave);

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
            .isInstanceOf(SessionCreatedEvent.class);
        this.registry.clear();

        sessionToSave.changeSessionId();
        this.repository.save(sessionToSave);

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isFalse();
    }

    @Test
    void updateMaxInactiveIntervalTest() throws InterruptedException {
        S sessionToSave = this.repository.createSession();
        sessionToSave.setMaxInactiveInterval(Duration.ofMinutes(30));
        this.repository.save(sessionToSave);

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
            .isInstanceOf(SessionCreatedEvent.class);
        this.registry.clear();

        S sessionToUpdate = this.repository.findById(sessionToSave.getId());
        sessionToUpdate.setLastAccessedTime(Instant.now());
        sessionToUpdate.setMaxInactiveInterval(Duration.ofSeconds(1));
        this.repository.save(sessionToUpdate);

        assertThat(this.registry.receivedEvent(sessionToUpdate.getId())).isTrue();
        assertThat(this.registry.<SessionExpiredEvent>getEvent(sessionToUpdate.getId()))
            .isInstanceOf(SessionExpiredEvent.class);
        assertThat(this.repository.findById(sessionToUpdate.getId())).isNull();
    }

    @Test
    void updateSessionAndExpireAfterOriginalTimeToLiveTest() throws InterruptedException {
        S sessionToSave = this.repository.createSession();
        this.repository.save(sessionToSave);

        assertThat(this.registry.receivedEvent(sessionToSave.getId())).isTrue();
        assertThat(this.registry.<SessionCreatedEvent>getEvent(sessionToSave.getId()))
            .isInstanceOf(SessionCreatedEvent.class);
        this.registry.clear();

        S sessionToUpdate = this.repository.findById(sessionToSave.getId());
        sessionToUpdate.setLastAccessedTime(Instant.now());
        this.repository.save(sessionToUpdate);

        assertThat(this.registry.receivedEvent(sessionToUpdate.getId())).isTrue();
        assertThat(this.registry.<SessionExpiredEvent>getEvent(sessionToUpdate.getId()))
            .isInstanceOf(SessionExpiredEvent.class);
        // Assert this after the expired event was received because it would otherwise do
        // its own expiration check and explicitly delete the session from Hazelcast
        // regardless of the TTL of the IMap entry.
        assertThat(this.repository.findById(sessionToUpdate.getId())).isNull();
    }
}
