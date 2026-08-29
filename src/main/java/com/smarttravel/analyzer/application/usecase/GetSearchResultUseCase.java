package com.smarttravel.analyzer.application.usecase;

import com.smarttravel.analyzer.application.dto.*;
import com.smarttravel.analyzer.application.mapper.DomainToDtoMapper;
import com.smarttravel.analyzer.application.search.SearchSessionStore;
import com.smarttravel.analyzer.domain.model.search.PackageFilter;
import com.smarttravel.analyzer.domain.service.PackageBundlerDomainService;
import com.smarttravel.analyzer.domain.service.PackageFilterDomainService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GetSearchResultUseCase {

    private final SearchSessionStore store;
    private final PackageFilterDomainService filters;
    private final PackageBundlerDomainService bundler;
    private final DomainToDtoMapper mapper;

    public GetSearchResultUseCase(SearchSessionStore store, PackageFilterDomainService filters,
                                  PackageBundlerDomainService bundler, DomainToDtoMapper mapper) {
        this.store = store;
        this.filters = filters;
        this.bundler = bundler;
        this.mapper = mapper;
    }

    public Optional<SearchResultResponse> result(String searchId, PackageFilter filter) {
        return store.find(searchId).map(session -> {
            // Filtra TODOS os candidatos e so entao escolhe os perfis: marcar "so voo direto" deve
            // trazer os melhores pacotes com voo direto, nao o que sobrou dos tres ja escolhidos.
            var matching = filters.apply(session.candidates(), filter);
            var visible = bundler.topRecommendations(matching);
            var packages = visible.stream().map(item -> mapper.toDto(item, matching, session.criteria())).toList();
            // Os bairros saem dos candidatos, nao do resultado filtrado: as opcoes nao podem sumir conforme se filtra.
            var neighborhoods = filters.summarise(session.candidates()).stream()
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
