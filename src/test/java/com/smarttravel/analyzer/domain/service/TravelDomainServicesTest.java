package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.carrental.*;
import com.smarttravel.analyzer.domain.model.history.*;
import com.smarttravel.analyzer.domain.model.lodging.HotelRating;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TravelDomainServicesTest {
    @Test void carRentalNormalizationAlwaysAddsFullCoverageWhenOnlyBasicInsuranceIsIncluded() {
        var usd = Currency.getInstance("USD");
        var offer = new CarRentalOffer("car-1", "Acme", CarCategory.COMPACT, new Money(BigDecimal.valueOf(40), usd), 3, InsuranceCoverageType.BASIC_CDW_TP, new Money(BigDecimal.valueOf(18), usd), new Money(BigDecimal.valueOf(25), usd), true, PickupMode.IN_TERMINAL, .9);
        var normalized = offer.normalizeWithFullInsurance();
        assertThat(normalized.totalPrice().amount()).isEqualByComparingTo("199.00");
        assertThat(normalized.includedCostAdjustments()).contains("zero_deductible_full_coverage");
    }

    @Test void priceTrendClassifiesExcellentDealAgainstHistory() {
        var usd = Currency.getInstance("USD");
        var points = List.of(new PriceHistoryPoint("NYC-LIS", LocalDate.now(), new Money(BigDecimal.valueOf(1000), usd)), new PriceHistoryPoint("NYC-LIS", LocalDate.now(), new Money(BigDecimal.valueOf(1100), usd)), new PriceHistoryPoint("NYC-LIS", LocalDate.now(), new Money(BigDecimal.valueOf(1200), usd)));
        var trend = new PriceTrendDomainService().calculateTrend("NYC-LIS", points);
        assertThat(trend.classify(new Money(BigDecimal.valueOf(900), usd))).isEqualTo(DealRating.EXCELLENT_DEAL);
    }

    @Test void bayesianRatingRewardsTrustworthyReviewVolume() {
        var service = new ValueScoringDomainService();
        assertThat(service.calculateBayesianRating(new HotelRating(10, 2), 8.0, 100).value()).isLessThan(service.calculateBayesianRating(new HotelRating(8.9, 2000), 8.0, 100).value());
    }
}
