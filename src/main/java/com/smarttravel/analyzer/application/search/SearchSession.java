package com.smarttravel.analyzer.application.search;

import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.search.*;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SearchSession {

    private final String id;
    private final SearchCriteria criteria;
    private final Instant createdAt = Instant.now();
    private final List<SourceStatus> sources = new CopyOnWriteArrayList<>();
    private volatile SearchStatus status = SearchStatus.BUSCANDO;
    private volatile List<TravelPackage> packages = List.of();
    private volatile List<TravelPackage> candidates = List.of();
    private volatile boolean demo;
    private volatile List<DateOption> dateOptions = List.of();

    public SearchSession(String id, SearchCriteria criteria) { this.id = id; this.criteria = criteria; }

    public String id() { return id; }
    public SearchCriteria criteria() { return criteria; }
    public Instant createdAt() { return createdAt; }
    public SearchStatus status() { return status; }
    public List<SourceStatus> sources() { return List.copyOf(sources); }
    public List<TravelPackage> packages() { return packages; }
    /** Todos os pacotes montados, antes de escolher os tres perfis. Filtros e bairros trabalham sobre esta lista. */
    public List<TravelPackage> candidates() { return candidates; }
    public boolean demo() { return demo; }
    public List<DateOption> dateOptions() { return dateOptions; }

    public void addSource(SourceStatus source) { sources.add(source); }
    public void status(SearchStatus value) { this.status = value; }
    public void packages(List<TravelPackage> value) { this.packages = List.copyOf(value); }
    public void candidates(List<TravelPackage> value) { this.candidates = List.copyOf(value); }
    public void demo(boolean value) { this.demo = value; }
    public void dateOptions(List<DateOption> value) { this.dateOptions = List.copyOf(value); }
}
