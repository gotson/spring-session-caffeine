package com.github.gotson.spring.session.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.gotson.spring.session.caffeine.CaffeineIndexedSessionRepository.CaffeineSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CaffeineIndexedSessionRepositoryTests {

    private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

    @SuppressWarnings("unchecked")
    private final Cache<String, MapSession> sessions = mock(Cache.class);

    private CaffeineIndexedSessionRepository repository;

    @BeforeEach
    void setUp() {
        this.repository = new CaffeineIndexedSessionRepository();
        ReflectionTestUtils.setField(this.repository, "sessions", sessions);
    }

    @Test
    void setApplicationEventPublisherNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> this.repository.setApplicationEventPublisher(null))
            .withMessage("ApplicationEventPublisher cannot be null");
    }

    @Test
    void setIndexResolverNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> this.repository.setIndexResolver(null))
            .withMessage("indexResolver cannot be null");
    }

    @Test
    void setExecutorNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> this.repository.setExecutor(null))
            .withMessage("executor cannot be null");
    }

    @Test
    void createSessionDefaultMaxInactiveInterval() {
        CaffeineSession session = this.repository.createSession();

        assertThat(session.getMaxInactiveInterval()).isEqualTo(new MapSession().getMaxInactiveInterval());

        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void createSessionCustomMaxInactiveInterval() {
        int interval = 1;
        this.repository.setDefaultMaxInactiveInterval(interval);

        CaffeineSession session = this.repository.createSession();

        assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(interval));

        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void saveNew() {
        CaffeineSession session = this.repository.createSession();
        this.repository.save(session);
        verify(this.sessions, times(1)).put(eq(session.getId()), eq(session.getDelegate()));

        this.repository.save(session);
        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void saveUpdatedAttribute() {
        CaffeineSession session = this.repository.createSession();

        session.setAttribute("testName", "testValue");
        this.repository.save(session);

        verify(this.sessions, times(1)).put(eq(session.getId()), eq(session.getDelegate()));
        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void removeAttribute() {
        CaffeineSession session = this.repository.createSession();

        session.removeAttribute("testName");
        this.repository.save(session);

        verify(this.sessions, times(1)).put(eq(session.getId()), eq(session.getDelegate()));
        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void saveUpdatedLastAccessedTime() {
        CaffeineSession session = this.repository.createSession();

        session.setLastAccessedTime(Instant.now());
        this.repository.save(session);

        verify(this.sessions, times(1)).put(eq(session.getId()), eq(session.getDelegate()));
        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void saveUpdatedMaxInactiveIntervalInSeconds() {
        CaffeineSession session = this.repository.createSession();

        session.setMaxInactiveInterval(Duration.ofSeconds(1));
        this.repository.save(session);

        verify(this.sessions, times(1)).put(eq(session.getId()), eq(session.getDelegate()));
        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void getSessionNotFound() {
        String sessionId = "testSessionId";

        CaffeineSession session = this.repository.findById(sessionId);

        assertThat(session).isNull();
        verify(this.sessions, times(1)).getIfPresent(eq(sessionId));
        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void getSessionExpired() {
        MapSession expired = new MapSession();
        expired.setLastAccessedTime(Instant.now().minusSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS + 1));
        given(this.sessions.getIfPresent(eq(expired.getId()))).willReturn(expired);

        CaffeineSession session = this.repository.findById(expired.getId());

        assertThat(session).isNull();
        verify(this.sessions, times(1)).getIfPresent(eq(expired.getId()));
        verify(this.sessions, times(1)).invalidate(eq(expired.getId()));
        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void getSessionFound() {
        MapSession saved = new MapSession();
        saved.setAttribute("savedName", "savedValue");
        given(this.sessions.getIfPresent(eq(saved.getId()))).willReturn(saved);

        CaffeineSession session = this.repository.findById(saved.getId());

        assertThat(session.getId()).isEqualTo(saved.getId());
        assertThat(session.<String>getAttribute("savedName")).isEqualTo("savedValue");
        verify(this.sessions, times(1)).getIfPresent(eq(saved.getId()));
        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void delete() {
        String sessionId = "testSessionId";

        this.repository.deleteById(sessionId);

        verify(this.sessions, times(1)).invalidate(eq(sessionId));
        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void findByIndexNameAndIndexValueUnknownIndexName() {
        String indexValue = "testIndexValue";

        Map<String, CaffeineSession> sessions = this.repository.findByIndexNameAndIndexValue("testIndexName",
            indexValue);

        assertThat(sessions).isEmpty();
        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void findByIndexNameAndIndexValuePrincipalIndexNameNotFound() {
        String principal = "username";

        when(this.sessions.asMap()).thenAnswer(x -> new ConcurrentHashMap<>());

        Map<String, CaffeineSession> sessions = this.repository
            .findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal);

        assertThat(sessions).isEmpty();
        verify(this.sessions, times(1)).asMap();
        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void findByIndexNameAndIndexValuePrincipalIndexNameFound() {
        String principal = "username";
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, "notused",
            AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContext securityContext = new SecurityContextImpl(authentication);
        ConcurrentMap<String, MapSession> saved = new ConcurrentHashMap<>(2);

        CaffeineSession saved1 = this.repository.createSession();
        saved1.setAttribute(SPRING_SECURITY_CONTEXT, securityContext);

        CaffeineSession saved2 = this.repository.createSession();
        saved2.setAttribute(SPRING_SECURITY_CONTEXT, securityContext);

        saved.put(saved1.getId(), saved1.getDelegate());
        saved.put(saved2.getId(), saved2.getDelegate());
        given(this.sessions.asMap()).willReturn(saved);

        Map<String, CaffeineSession> sessions = this.repository
            .findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal);

        assertThat(sessions).hasSize(2);
        verify(this.sessions, times(1)).asMap();
        verifyNoMoreInteractions(this.sessions);
    }

    @Test
    void getAttributeNamesAndRemove() {
        CaffeineSession session = this.repository.createSession();
        session.setAttribute("attribute1", "value1");
        session.setAttribute("attribute2", "value2");

        for (String attributeName : session.getAttributeNames()) {
            session.removeAttribute(attributeName);
        }

        assertThat(session.getAttributeNames()).isEmpty();
    }
}