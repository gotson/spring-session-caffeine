package com.github.gotson.spring.session.caffeine;

import com.github.gotson.spring.session.caffeine.CaffeineIndexedSessionRepository.CaffeineSession;
import com.github.gotson.spring.session.caffeine.config.annotation.web.http.EnableCaffeineHttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
public class CaffeineIndexedSessionRepositoryITests {

    @EnableCaffeineHttpSession
    @Configuration
    static class CaffeineSessionConfig {
    }

    private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

    @Autowired
    private CaffeineIndexedSessionRepository repository;

    @Test
    void changeSessionIdWhenOnlyChangeId() {
        String attrName = "changeSessionId";
        String attrValue = "changeSessionId-value";
        CaffeineSession toSave = this.repository.createSession();
        toSave.setAttribute(attrName, attrValue);

        this.repository.save(toSave);

        CaffeineSession findById = this.repository.findById(toSave.getId());

        assertThat(findById.<String>getAttribute(attrName)).isEqualTo(attrValue);

        String originalFindById = findById.getId();
        String changeSessionId = findById.changeSessionId();

        this.repository.save(findById);

        assertThat(this.repository.findById(originalFindById)).isNull();

        CaffeineSession findByChangeSessionId = this.repository.findById(changeSessionId);

        assertThat(findByChangeSessionId.<String>getAttribute(attrName)).isEqualTo(attrValue);

        this.repository.deleteById(changeSessionId);
    }

    @Test
    void changeSessionIdWhenChangeTwice() {
        CaffeineSession toSave = this.repository.createSession();

        this.repository.save(toSave);

        String originalId = toSave.getId();
        String changeId1 = toSave.changeSessionId();
        String changeId2 = toSave.changeSessionId();

        this.repository.save(toSave);

        assertThat(this.repository.findById(originalId)).isNull();
        assertThat(this.repository.findById(changeId1)).isNull();
        assertThat(this.repository.findById(changeId2)).isNotNull();

        this.repository.deleteById(changeId2);
    }

    @Test
    void changeSessionIdWhenSetAttributeOnChangedSession() {
        String attrName = "changeSessionId";
        String attrValue = "changeSessionId-value";

        CaffeineSession toSave = this.repository.createSession();

        this.repository.save(toSave);

        CaffeineSession findById = this.repository.findById(toSave.getId());

        findById.setAttribute(attrName, attrValue);

        String originalFindById = findById.getId();
        String changeSessionId = findById.changeSessionId();

        this.repository.save(findById);

        assertThat(this.repository.findById(originalFindById)).isNull();

        CaffeineSession findByChangeSessionId = this.repository.findById(changeSessionId);

        assertThat(findByChangeSessionId.<String>getAttribute(attrName)).isEqualTo(attrValue);

        this.repository.deleteById(changeSessionId);
    }

    @Test
    void changeSessionIdWhenHasNotSaved() {
        CaffeineSession toSave = this.repository.createSession();
        String originalId = toSave.getId();
        toSave.changeSessionId();

        this.repository.save(toSave);

        assertThat(this.repository.findById(toSave.getId())).isNotNull();
        assertThat(this.repository.findById(originalId)).isNull();

        this.repository.deleteById(toSave.getId());
    }

    @Test
    void attemptToUpdateSessionAfterDelete() {
        CaffeineSession session = this.repository.createSession();
        String sessionId = session.getId();
        this.repository.save(session);
        session = this.repository.findById(sessionId);
        session.setAttribute("attributeName", "attributeValue");
        this.repository.deleteById(sessionId);
        this.repository.save(session);

        assertThat(this.repository.findById(sessionId)).isNull();
    }

    @Test
    void createAndUpdateSession() {
        CaffeineSession session = this.repository.createSession();
        String sessionId = session.getId();

        this.repository.save(session);

        session = this.repository.findById(sessionId);
        session.setAttribute("attributeName", "attributeValue");

        this.repository.save(session);

        assertThat(this.repository.findById(sessionId)).isNotNull();

        this.repository.deleteById(sessionId);
    }

    @Test
    void createSessionWithSecurityContextAndFindById() {
        CaffeineSession session = this.repository.createSession();
        String sessionId = session.getId();

        Authentication authentication = new UsernamePasswordAuthenticationToken("saves-" + System.currentTimeMillis(),
            "password", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        session.setAttribute(SPRING_SECURITY_CONTEXT, securityContext);

        this.repository.save(session);

        assertThat(this.repository.findById(sessionId)).isNotNull();

        this.repository.deleteById(sessionId);
    }

    @Test
    void createAndUpdateSessionWhileKeepingOriginalTimeToLiveConfiguredOnRepository() {
        final Duration defaultSessionTimeout = Duration.ofSeconds(1800);

        CaffeineSession session = this.repository.createSession();
        String sessionId = session.getId();
        this.repository.save(session);

        assertThat(session.getMaxInactiveInterval()).isEqualTo(defaultSessionTimeout);

        session = this.repository.findById(sessionId);
        session.setLastAccessedTime(Instant.now());
        this.repository.save(session);

        session = this.repository.findById(sessionId);
        assertThat(session.getMaxInactiveInterval()).isEqualTo(defaultSessionTimeout);
    }

    @Test
    void createAndUpdateSessionWhileKeepingTimeToLiveSetOnSession() {
        final Duration individualSessionTimeout = Duration.ofSeconds(23);

        CaffeineSession session = this.repository.createSession();
        session.setMaxInactiveInterval(individualSessionTimeout);
        String sessionId = session.getId();
        this.repository.save(session);

        assertThat(session.getMaxInactiveInterval()).isEqualTo(individualSessionTimeout);

        session = this.repository.findById(sessionId);
        session.setAttribute("attribute", "value");
        this.repository.save(session);

        session = this.repository.findById(sessionId);
        assertThat(session.getMaxInactiveInterval()).isEqualTo(individualSessionTimeout);
    }
}
