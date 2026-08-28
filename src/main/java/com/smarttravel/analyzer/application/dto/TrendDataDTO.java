package com.smarttravel.analyzer.application.dto;

import com.smarttravel.analyzer.domain.model.history.DealRating;
import java.math.BigDecimal;

public record TrendDataDTO(String marketKey, BigDecimal mean, BigDecimal median, double standardDeviation, int sampleSize, DealRating currentDealRating) {}
