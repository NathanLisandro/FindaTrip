package com.smarttravel.analyzer.application.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record SearchCriteriaRequest(@NotBlank String origin, @NotBlank String destination, @Future LocalDate departureDate, @Future LocalDate returnDate, @Min(1) int travelers, boolean checkedBagRequested, boolean carRequired) {}
