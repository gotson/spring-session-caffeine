package com.github.gotson.spring.session.caffeine;

import org.springframework.context.ApplicationListener;
import org.springframework.session.events.AbstractSessionEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SessionEventRegistry implements ApplicationListener<AbstractSessionEvent> {

    private final Map<String, AbstractSessionEvent> events = new HashMap<>();

    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    @Override
    public void onApplicationEvent(AbstractSessionEvent event) {
        String sessionId = event.getSessionId();
        this.events.put(sessionId, event);
        Object lock = getLock(sessionId);
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    void clear() {
        this.events.clear();
        this.locks.clear();
    }

    boolean receivedEvent(String sessionId) throws InterruptedException {
        return waitForEvent(sessionId) != null;
    }

    @SuppressWarnings("unchecked")
    <E extends AbstractSessionEvent> E getEvent(String sessionId) throws InterruptedException {
        return waitForEvent(sessionId);
    }

    @SuppressWarnings("unchecked")
    private <E extends AbstractSessionEvent> E waitForEvent(String sessionId) throws InterruptedException {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            if (!this.events.containsKey(sessionId)) {
                lock.wait(10000);
            }
        }
        return (E) this.events.get(sessionId);
    }

    private Object getLock(String sessionId) {
        return this.locks.computeIfAbsent(sessionId, (k) -> new Object());
    }
}
