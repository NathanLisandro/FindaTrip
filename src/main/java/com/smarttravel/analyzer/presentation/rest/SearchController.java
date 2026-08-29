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
            @RequestParam(required = false) String neighborhood) {
        var filter = new PackageFilter(maxPrice, minRating, directFlightOnly, breakfastIncluded, freeCancellation, neighborhood);
        return getResult.result(searchId, filter).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
