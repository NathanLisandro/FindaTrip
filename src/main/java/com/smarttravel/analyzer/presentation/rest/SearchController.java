package com.smarttravel.analyzer.presentation.rest;

import com.smarttravel.analyzer.application.dto.*;
import com.smarttravel.analyzer.application.usecase.*;
import com.smarttravel.analyzer.domain.model.search.PackageFilter;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final StartSearchUseCase startSearch;
    private final GetSearchResultUseCase getResult;

    public SearchController(StartSearchUseCase startSearch, GetSearchResultUseCase getResult) {
        this.startSearch = startSearch;
        this.getResult = getResult;
    }

    @PostMapping
    public ResponseEntity<SearchStartedResponse> start(@Valid @RequestBody SearchCriteriaRequest request) {
        return ResponseEntity.accepted().body(new SearchStartedResponse(startSearch.start(request)));
    }

    @GetMapping("/{searchId}")
    public ResponseEntity<SearchResultResponse> result(
            @PathVariable String searchId,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "false") boolean directFlightOnly,
            @RequestParam(defaultValue = "false") boolean breakfastIncluded,
            @RequestParam(defaultValue = "false") boolean freeCancellation,
            @RequestParam(required = false) String neighborhood,
            @RequestParam(required = false) java.util.List<String> stayTypes) {
        var filter = new PackageFilter(maxPrice, minRating, directFlightOnly, breakfastIncluded,
            freeCancellation, neighborhood, tipos(stayTypes));
        return getResult.result(searchId, filter).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Tipo desconhecido e ignorado em vez de derrubar a busca com 400. */
    private static java.util.Set<com.smarttravel.analyzer.domain.model.lodging.StayType> tipos(java.util.List<String> nomes) {
        if (nomes == null) return java.util.Set.of();
        var conjunto = java.util.EnumSet.noneOf(com.smarttravel.analyzer.domain.model.lodging.StayType.class);
        for (var nome : nomes) {
            try { conjunto.add(com.smarttravel.analyzer.domain.model.lodging.StayType.valueOf(nome)); }
            catch (IllegalArgumentException desconhecido) { /* ignora */ }
        }
        return conjunto;
    }
}
