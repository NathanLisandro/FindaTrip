package com.smarttravel.analyzer.application.usecase;

import com.smarttravel.analyzer.application.dto.*;
import com.smarttravel.analyzer.application.mapper.DomainToDtoMapper;
import com.smarttravel.analyzer.application.search.SearchSessionStore;
import com.smarttravel.analyzer.domain.model.search.PackageFilter;
import com.smarttravel.analyzer.domain.service.PackageFilterDomainService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GetSearchResultUseCase {

    private final SearchSessionStore store;
    private final PackageFilterDomainService filters;
    private final DomainToDtoMapper mapper;

    public GetSearchResultUseCase(SearchSessionStore store, PackageFilterDomainService filters, DomainToDtoMapper mapper) {
        this.store = store;
        this.filters = filters;
        this.mapper = mapper;
    }

    public Optional<SearchResultResponse> result(String searchId, PackageFilter filter) {
        return store.find(searchId).map(session -> {
            var visible = filters.apply(session.packages(), filter);
            var packages = visible.stream().map(item -> mapper.toDto(item, session.packages(), session.criteria())).toList();
            var neighborhoods = filters.summarise(session.packages()).stream()
                .map(summary -> new NeighborhoodDTO(summary.neighborhood(), summary.packages(), summary.cheapest().amount()))
                .toList();
            var dateOptions = session.dateOptions().stream()
                .map(option -> new DateOptionDTO(option.departureDate(), option.returnDate(),
                    option.total().amount(), option.difference().amount()))
                .toList();
            return new SearchResultResponse(session.id(), session.status(), session.demo(),
                session.sources().stream().map(mapper::toDto).toList(), packages, neighborhoods, List.copyOf(dateOptions));
        });
    }
}
