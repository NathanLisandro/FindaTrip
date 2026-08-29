package com.smarttravel.analyzer.application.dto;

import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record SearchCriteriaRequest(@NotBlank String origin, @NotBlank String destination,
                                    @Future LocalDate departureDate, @Future LocalDate returnDate,
                                    @Min(1) int travelers, boolean checkedBagRequested,
                                    boolean carRequired, boolean flexibleDates) {

    public SearchCriteria toDomain() {
        return new SearchCriteria(origin, destination, departureDate, returnDate, travelers, checkedBagRequested, carRequired);
    }
}
