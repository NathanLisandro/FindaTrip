package com.smarttravel.analyzer.domain.model.history;

import com.smarttravel.analyzer.domain.model.shared.Money;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public record RoutePriceTrend(String marketKey, Money mean, Money median, double standardDeviation, int sampleSize) {
    public DealRating classify(Money current) {
        double currentAmount = current.amount().doubleValue();
        double meanAmount = mean.amount().doubleValue();
        if (currentAmount <= meanAmount - 0.75 * standardDeviation || currentAmount <= meanAmount * 0.85) return DealRating.EXCELLENT_DEAL;
        if (currentAmount > meanAmount + 0.75 * standardDeviation) return DealRating.OVERPRICED;
        return DealRating.FAIR_PRICE;
    }
    public static RoutePriceTrend from(String key, List<PriceHistoryPoint> points) {
        var sorted = points.stream().map(PriceHistoryPoint::normalizedPrice).sorted().toList();
        Money first = sorted.getFirst();
        double avg = sorted.stream().mapToDouble(m -> m.amount().doubleValue()).average().orElseThrow();
        double variance = sorted.stream().mapToDouble(m -> Math.pow(m.amount().doubleValue() - avg, 2)).average().orElse(0);
        Money median = sorted.get(sorted.size()/2);
        return new RoutePriceTrend(key, new Money(BigDecimal.valueOf(avg), first.currency()), median, Math.sqrt(variance), sorted.size());
    }
}
