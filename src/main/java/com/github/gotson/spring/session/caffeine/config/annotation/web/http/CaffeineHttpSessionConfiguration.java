package com.github.gotson.spring.session.caffeine.config.annotation.web.http;

import com.github.gotson.spring.session.caffeine.CaffeineIndexedSessionRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.IndexResolver;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.web.http.SessionRepositoryFilter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exposes the {@link SessionRepositoryFilter} as a bean named
 * {@code springSessionRepositoryFilter}.
 *
 * @see EnableCaffeineHttpSession
 */
@Configuration(proxyBeanMethods = false)
public class CaffeineHttpSessionConfiguration extends SpringHttpSessionConfiguration implements ImportAware {

    private Integer maxInactiveIntervalInSeconds = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

    private ApplicationEventPublisher applicationEventPublisher;

    private IndexResolver<Session> indexResolver;

    private List<SessionRepositoryCustomizer<CaffeineIndexedSessionRepository>> sessionRepositoryCustomizers;

    @Bean
    public FindByIndexNameSessionRepository<?> sessionRepository() {
        return createCaffeineSessionRepository();
    }

    public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
        this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
    }

    @Autowired
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Autowired(required = false)
    public void setIndexResolver(IndexResolver<Session> indexResolver) {
        this.indexResolver = indexResolver;
    }

    @Autowired(required = false)
    public void setSessionRepositoryCustomizer(
        ObjectProvider<SessionRepositoryCustomizer<CaffeineIndexedSessionRepository>> sessionRepositoryCustomizers) {
        this.sessionRepositoryCustomizers = sessionRepositoryCustomizers.orderedStream().collect(Collectors.toList());
    }

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        Map<String, Object> attributeMap = importMetadata
            .getAnnotationAttributes(EnableCaffeineHttpSession.class.getName());
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(attributeMap);
        this.maxInactiveIntervalInSeconds = attributes.getNumber("maxInactiveIntervalInSeconds");
    }

    private CaffeineIndexedSessionRepository createCaffeineSessionRepository() {
        CaffeineIndexedSessionRepository sessionRepository = new CaffeineIndexedSessionRepository();
        sessionRepository.setApplicationEventPublisher(this.applicationEventPublisher);
        if (this.indexResolver != null) {
            sessionRepository.setIndexResolver(this.indexResolver);
        }
        sessionRepository.setDefaultMaxInactiveInterval(this.maxInactiveIntervalInSeconds);
        this.sessionRepositoryCustomizers
            .forEach((sessionRepositoryCustomizer) -> sessionRepositoryCustomizer.customize(sessionRepository));
        return sessionRepository;
    }
}
