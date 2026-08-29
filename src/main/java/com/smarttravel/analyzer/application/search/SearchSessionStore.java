package com.smarttravel.analyzer.application.search;

import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SearchSessionStore {

    static final Duration TTL = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, SearchSession> sessions = new ConcurrentHashMap<>();

    public SearchSession create(SearchCriteria criteria) {
        purgeExpired();
        var session = new SearchSession(UUID.randomUUID().toString(), criteria);
        sessions.put(session.id(), session);
        return session;
    }

    public Optional<SearchSession> find(String id) { return Optional.ofNullable(sessions.get(id)); }

    public void purgeExpired() {
        var cutoff = Instant.now().minus(TTL);
        sessions.values().removeIf(session -> session.createdAt().isBefore(cutoff));
    }
}
