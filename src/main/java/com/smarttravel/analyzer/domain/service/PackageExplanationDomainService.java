package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PackageExplanationDomainService {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    private static final Map<String, String> ADJUSTMENTS = Map.of(
        "mandatory_airport_taxes", "taxa de embarque",
        "checked_baggage", "bagagem despachada",
        "service_fees", "taxa de serviço",
        "city_taxes", "taxa municipal",
        "resort_fees", "taxa de resort",
        "airport_pickup_fee", "retirada no aeroporto",
        "zero_deductible_full_coverage", "seguro sem franquia");

    public String translateAdjustment(String code) { return ADJUSTMENTS.getOrDefault(code, code); }

    public String explain(TravelPackage chosen, List<TravelPackage> candidates) {
        var reasons = new java.util.ArrayList<String>();

        var hidden = chosen.hiddenCosts().amount();
        if (hidden.compareTo(BigDecimal.ZERO) > 0) {
            var fees = chosen.costAdjustments().stream().map(this::translateAdjustment).toList();
            reasons.add("O preço anunciado esconde " + money(hidden) + " em " + String.join(", ", fees) + ".");
        }

        boolean cheapest = candidates.stream().noneMatch(other -> other.totalPrice().compareTo(chosen.totalPrice()) < 0);
        if (cheapest) reasons.add("É o mais barato depois de somar tudo.");

        var topRated = candidates.stream()
            .max(Comparator.comparingDouble(candidate -> candidate.lodging().offer().rating().average()))
            .orElse(chosen);
        var chosenRating = chosen.lodging().offer().rating();
        var topRating = topRated.lodging().offer().rating();
        if (topRating.average() > chosenRating.average() && chosenRating.reviewCount() > topRating.reviewCount()) {
            reasons.add("A nota " + number(chosenRating.average()) + " com " + count(chosenRating.reviewCount())
                + " avaliações é mais confiável que a nota " + number(topRating.average())
                + " com " + count(topRating.reviewCount()) + " avaliações.");
        }

        return String.join(" ", reasons);
    }

    private static String money(BigDecimal amount) { return NumberFormat.getCurrencyInstance(BR).format(amount); }
    private static String number(double value) { return NumberFormat.getNumberInstance(BR).format(value); }
    private static String count(int value) { return NumberFormat.getIntegerInstance(BR).format(value); }
}
