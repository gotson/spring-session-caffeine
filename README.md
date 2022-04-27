# Spring Session Caffeine [![GitHub Workflow Status (branch)](https://img.shields.io/github/workflow/status/gotson/spring-session-caffeine/test/master)](https://github.com/gotson/spring-session-caffeine/actions/workflows/test.yml)

Provides a `SessionRepository` implementation backed by a [Caffeine](https://github.com/ben-manes/caffeine) cache.

## Features

- respond to entries being added, evicted, and removed from the registry causes these events to trigger publishing
  of `SessionCreatedEvent`, `SessionExpiredEvent`, and `SessionDeletedEvent` events (respectively) through
  the `ApplicationEventPublisher`
- automatically purge expired sessions
- configure underlying cache by setting a specific `Scheduler` or `Executor`
- implements `FindByIndexNameSessionRepository`, which can be used with `SpringSessionBackedSessionRegistry` if you need
  to support Spring Security concurrent session control

## When to use it?

_Spring Session Caffeine_ is a good candidate when you need more capabilities than the default `MapSessionRepository`,
like events firing or automatic purging of expired sessions, or when you need a `FindByIndexNameSessionRepository`.

If you need those extra capabilities, you may consider _Spring Session Caffeine_ instead of other Spring Session Modules
in the following cases:

- Single instance. _Spring Session Caffeine_ will be more lightweight than _Spring Session Redis_ or _Spring Session
  Hazelcast_, as those solutions depend on external systems, while Caffeine is a pure Java implementation. Caffeine is
  not a distributed cache, so it will only work with a single instance.
- No JDBC database. While _Spring Session JDBC_ can be a good candidate for a single instance service, you may not be
  using a database already.
- SQLite. When using _Spring Session JDBC_ with SQLite, the high number of writes can impact the performances of your
  application when sharing the SQLite database between sessions and the rest of your application. In that case _Spring
  Session Caffeine_ can be a good alternative.

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.github.gotson/spring-session-caffeine)](https://search.maven.org/artifact/com.github.gotson/spring-session-caffeine) [![javadoc](https://javadoc.io/badge2/com.github.gotson/spring-session-caffeine/javadoc.svg)](https://javadoc.io/doc/com.github.gotson/spring-session-caffeine)

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
