package com.github.gotson.spring.session.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.DelegatingIndexResolver;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.IndexResolver;
import org.springframework.session.MapSession;
import org.springframework.session.PrincipalNameIndexResolver;
import org.springframework.session.Session;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A {@link org.springframework.session.SessionRepository} implementation that stores
 * sessions in a Caffeine {@link Cache}.
 *
 * <p>
 * This implementation publish the Spring Session events with the given {@link ApplicationEventPublisher}.
 *
 * <ul>
 * <li>entryAdded - {@link SessionCreatedEvent}</li>
 * <li>entryEvicted - {@link SessionExpiredEvent}</li>
 * <li>entryRemoved - {@link SessionDeletedEvent}</li>
 * </ul>
 */
public class CaffeineIndexedSessionRepository
    implements FindByIndexNameSessionRepository<CaffeineIndexedSessionRepository.CaffeineSession> {

    /**
     * The principal name custom attribute name.
     */
    public static final String PRINCIPAL_NAME_ATTRIBUTE = "principalName";

    private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

    private ApplicationEventPublisher eventPublisher = (event) -> {
    };

    /**
     * If non-null, this value is used to override
     * {@link MapSession#setMaxInactiveInterval(Duration)}.
     */
    private Integer defaultMaxInactiveInterval;

    private IndexResolver<Session> indexResolver = new DelegatingIndexResolver<>(new PrincipalNameIndexResolver<>());

    private Executor executor;

    private Scheduler scheduler;

    private Cache<String, MapSession> sessions;

    @PostConstruct
    public void init() {
        Caffeine<String, MapSession> builder = Caffeine.newBuilder()
            .removalListener(this::removalListener)
            .expireAfter(new Expiry<>() {
                @Override
                public long expireAfterCreate(@NonNull String key, @NonNull MapSession value, long currentTime) {
                    return value.getMaxInactiveInterval().toNanos();
                }

                @Override
                public long expireAfterUpdate(@NonNull String key, @NonNull MapSession value, long currentTime, long currentDuration) {
                    return value.getMaxInactiveInterval().toNanos();
                }

                @Override
                public long expireAfterRead(@NonNull String key, @NonNull MapSession value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            });
        if (this.executor != null) builder.executor(this.executor);
        if (this.scheduler != null) builder.scheduler(this.scheduler);

        this.sessions = builder.build();
    }

    /**
     * Sets the {@link ApplicationEventPublisher} that is used to publish
     * {@link AbstractSessionEvent session events}. The default is to not publish session
     * events.
     *
     * @param applicationEventPublisher the {@link ApplicationEventPublisher} that is used
     *                                  to publish session events. Cannot be null.
     */
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        Assert.notNull(applicationEventPublisher, "ApplicationEventPublisher cannot be null");
        this.eventPublisher = applicationEventPublisher;
    }

    /**
     * Set the maximum inactive interval in seconds between requests before newly created
     * sessions will be invalidated. A negative time indicates that the session will never
     * time out. The default is 1800 (30 minutes).
     *
     * @param defaultMaxInactiveInterval the maximum inactive interval in seconds
     */
    public void setDefaultMaxInactiveInterval(Integer defaultMaxInactiveInterval) {
        this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
    }

    /**
     * Set the {@link IndexResolver} to use.
     *
     * @param indexResolver the index resolver
     */
    public void setIndexResolver(IndexResolver<Session> indexResolver) {
        Assert.notNull(indexResolver, "indexResolver cannot be null");
        this.indexResolver = indexResolver;
    }

    /**
     * Sets the {@link Executor} that is used to perform
     * removal operations. The default is Caffeine's default.
     *
     * @param executor the {@link Executor} that is used
     *                 to perform removal operations on the cache. Cannot be null.
     */
    public void setExecutor(Executor executor) {
        Assert.notNull(executor, "executor cannot be null");
        this.executor = executor;
    }

    /**
     * Sets the {@link Scheduler} that is used to perform
     * removal operations. The default is Caffeine's default.
     *
     * @param scheduler the {@link Scheduler} that is used
     *                  to perform removal operations on the cache. Cannot be null.
     */
    public void setScheduler(Scheduler scheduler) {
        Assert.notNull(scheduler, "scheduler cannot be null");
        this.scheduler = scheduler;
    }

    private void removalListener(String key, MapSession session, RemovalCause cause) {
        if (session != null) {
            switch (cause) {
                case EXPLICIT:
                    eventPublisher.publishEvent(new SessionDeletedEvent(this, session));
                    break;
                case REPLACED:
                    break;
                case COLLECTED:
                case EXPIRED:
                case SIZE:
                    eventPublisher.publishEvent(new SessionExpiredEvent(this, session));
                    break;
            }
        }
    }

    @Override
    public Map<String, CaffeineSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
        if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName) || indexValue == null) {
            return Collections.emptyMap();
        }
        Collection<MapSession> sessions = this.sessions.asMap().values().stream()
            .filter(mapSession -> indexValue.equals(mapSession.getAttribute(PRINCIPAL_NAME_ATTRIBUTE))).toList();
        Map<String, CaffeineSession> sessionMap = new HashMap<>(sessions.size());
        for (MapSession session : sessions) {
            sessionMap.put(session.getId(), new CaffeineSession(session, false));
        }
        return sessionMap;
    }

    @Override
    public CaffeineSession createSession() {
        MapSession cached = new MapSession();
        if (this.defaultMaxInactiveInterval != null) {
            cached.setMaxInactiveInterval(Duration.ofSeconds(this.defaultMaxInactiveInterval));
        }
        return new CaffeineSession(cached, true);
    }

    @Override
    public void save(CaffeineSession session) {
        if (session.isNew) {
            this.sessions.put(session.getId(), session.getDelegate());
            eventPublisher.publishEvent(new SessionCreatedEvent(this, session));
        } else if (session.sessionIdChanged) {
            this.sessions.invalidate(session.originalId);
            session.originalId = session.getId();
            this.sessions.put(session.getId(), new MapSession(session.getDelegate()));
        } else if (session.hasChanges()) {
            if (this.sessions.getIfPresent(session.getId()) != null) {
                this.sessions.put(session.getId(), new MapSession(session.getDelegate()));
            }
        }
        session.clearChangeFlags();
    }

    @Override
    public CaffeineSession findById(String id) {
        MapSession saved = this.sessions.getIfPresent(id);
        if (saved == null) {
            return null;
        }
        if (saved.isExpired()) {
            deleteById(saved.getId());
            return null;
        }
        return new CaffeineSession(saved, false);
    }

    @Override
    public void deleteById(String id) {
        this.sessions.invalidate(id);
    }

    /**
     * A custom implementation of {@link Session} that uses a {@link MapSession} as the
     * basis for its mapping. It keeps track if changes have been made since last save.
     */
    final class CaffeineSession implements Session {

        private MapSession delegate;

        private boolean isNew;

        private boolean sessionIdChanged;

        private boolean lastAccessedTimeChanged;

        private boolean maxInactiveIntervalChanged;

        private boolean attributesChanged;

        private String originalId;

        CaffeineSession(MapSession cached, boolean isNew) {
            this.delegate = new MapSession(cached);
            this.isNew = isNew;
            this.originalId = cached.getId();
        }

        @Override
        public String getId() {
            return delegate.getId();
        }

        @Override
        public String changeSessionId() {
            this.delegate = new MapSession(this.delegate);
            String newSessionId = this.delegate.changeSessionId();
            this.sessionIdChanged = true;
            return newSessionId;
        }

        @Override
        public <T> T getAttribute(String attributeName) {
            return delegate.getAttribute(attributeName);
        }

        @Override
        public Set<String> getAttributeNames() {
            return delegate.getAttributeNames();
        }

        @Override
        public void setAttribute(String attributeName, Object attributeValue) {
            this.delegate.setAttribute(attributeName, attributeValue);
            this.attributesChanged = true;
            if (SPRING_SECURITY_CONTEXT.equals(attributeName)) {
                Map<String, String> indexes = CaffeineIndexedSessionRepository.this.indexResolver
                    .resolveIndexesFor(this);
                String principal = (attributeValue != null) ? indexes.get(PRINCIPAL_NAME_INDEX_NAME) : null;
                this.delegate.setAttribute(PRINCIPAL_NAME_ATTRIBUTE, principal);
            }
        }

        @Override
        public void removeAttribute(String attributeName) {
            delegate.removeAttribute(attributeName);
            this.attributesChanged = true;
        }

        @Override
        public Instant getCreationTime() {
            return delegate.getCreationTime();
        }

        @Override
        public void setLastAccessedTime(Instant lastAccessedTime) {
            this.delegate.setLastAccessedTime(lastAccessedTime);
            this.lastAccessedTimeChanged = true;
        }

        @Override
        public Instant getLastAccessedTime() {
            return delegate.getLastAccessedTime();
        }

        @Override
        public void setMaxInactiveInterval(Duration interval) {
            Assert.notNull(interval, "interval must not be null");
            this.delegate.setMaxInactiveInterval(interval);
            this.maxInactiveIntervalChanged = true;
        }

        @Override
        public Duration getMaxInactiveInterval() {
            return delegate.getMaxInactiveInterval();
        }

        @Override
        public boolean isExpired() {
            return delegate.isExpired();
        }

        MapSession getDelegate() {
            return this.delegate;
        }

        boolean hasChanges() {
            return (this.lastAccessedTimeChanged || this.maxInactiveIntervalChanged || this.attributesChanged);
        }

        void clearChangeFlags() {
            this.isNew = false;
            this.lastAccessedTimeChanged = false;
            this.sessionIdChanged = false;
            this.maxInactiveIntervalChanged = false;
            this.attributesChanged = false;
        }
    }
}
