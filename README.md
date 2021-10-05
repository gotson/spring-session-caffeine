# Spring Session Caffeine [![GitHub Workflow Status (branch)](https://img.shields.io/github/workflow/status/gotson/spring-session-caffeine/test/master)](https://github.com/gotson/spring-session-caffeine/actions/workflows/test.yml)

Provides `SessionRepository` implementation backed by a [Caffeine](https://github.com/ben-manes/caffeine) cache.

## Motivation

I needed Spring Session in order to use custom `HttpSessionIdResolver`, in order to read/write session IDs to headers.
My application is fairly small and doesn't need clustered sessions like Redis or Hazelcast.

The Spring Session `MapSessionRepository` lacks some capabilities:

- firing Session events
- automatically purge expired sessions
- does not implement `FindByIndexNameSessionRepository`, which is required for integration with Spring Security

Spring Session Caffeine implements all those capabilities.

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.github.gotson/spring-session-caffeine)](https://search.maven.org/artifact/com.github.gotson/spring-session-caffeine)

### Gradle

```groovy
implementation "com.github.gotson:spring-session-caffeine:{version}"
```

### Gradle (Kotlin DSL)

```kotlin
implementation("com.github.gotson:spring-session-caffeine:{version}")
```

### Maven

```xml

<dependency>
    <groupId>com.github.gotson</groupId>
    <artifactId>spring-session-caffeine</artifactId>
    <version>{version}</version>
</dependency>
```

## Usage

### Simple

```java

@EnableCaffeineHttpSession(maxInactiveIntervalInSeconds = 3600)
public class Config {
}
```

### Advanced

```java

@EnableCaffeineHttpSession
public class Config {
    @Bean
    SessionRepositoryCustomizer<CaffeineIndexedSessionRepository> customize() {
        return (sessionRepository -> {
            sessionRepository.setDefaultMaxInactiveInterval(Duration.ofDays(7).getSeconds());
            sessionRepository.setExecutor(Executors.newFixedThreadPool(1));
            sessionRepository.setScheduler(Scheduler.forScheduledExecutorService(Executors.newScheduledThreadPool(1)));
        }
        );
    }
}
```