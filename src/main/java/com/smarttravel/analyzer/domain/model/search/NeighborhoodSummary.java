package com.smarttravel.analyzer.domain.model.search;

import com.smarttravel.analyzer.domain.model.shared.Money;

public record NeighborhoodSummary(String neighborhood, int packages, Money cheapest) {}
