package com.smarttravel.analyzer.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DateOptionDTO(LocalDate departureDate, LocalDate returnDate, BigDecimal total, BigDecimal difference) {}
