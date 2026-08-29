package com.smarttravel.analyzer.application.search;

import com.smarttravel.analyzer.domain.model.shared.Money;
import java.time.LocalDate;

public record DateOption(LocalDate departureDate, LocalDate returnDate, Money total, Money difference) {}
