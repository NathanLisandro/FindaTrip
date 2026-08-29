package com.smarttravel.analyzer.application.usecase;

import com.smarttravel.analyzer.application.dto.SearchCriteriaRequest;
import com.smarttravel.analyzer.application.search.*;
import com.smarttravel.analyzer.application.service.SearchRunner;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Service;

@Service
public class StartSearchUseCase {

    private final SearchSessionStore store;
    private final SearchRunner runner;
    private final Executor executor;

    public StartSearchUseCase(SearchSessionStore store, SearchRunner runner, Executor executor) {
        this.store = store;
        this.runner = runner;
        this.executor = executor;
    }

    public String start(SearchCriteriaRequest request) {
        var session = store.create(request.toDomain());
        executor.execute(() -> runner.run(session, request.flexibleDates()));
        return session.id();
    }
}
