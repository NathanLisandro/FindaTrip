package com.smarttravel.analyzer.application.dto;

import com.smarttravel.analyzer.domain.model.packagebundle.PackageBundleType;
import java.math.BigDecimal;
import java.util.List;

public record PackageDTO(String id, PackageBundleType recommendationType, BigDecimal advertisedPrice,
                         BigDecimal realCost, BigDecimal hiddenCosts, String currency,
                         double valueScore, double qualityScore, double convenienceScore,
                         List<String> included, String why,
                         String airline, int stops,
                         String lodgingName, String neighborhood, double lodgingRating, int lodgingReviews,
                         String carSupplier, String carCategory,
                         List<BookingLinkDTO> links) {}
