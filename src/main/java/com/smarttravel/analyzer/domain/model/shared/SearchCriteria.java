package com.smarttravel.analyzer.domain.model.shared;

import java.time.LocalDate;

public record SearchCriteria(String origin, String destination, LocalDate departureDate, LocalDate returnDate, int travelers, boolean checkedBagRequested, boolean carRequired) {}
