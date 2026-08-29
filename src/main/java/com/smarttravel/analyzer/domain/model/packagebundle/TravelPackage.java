package com.smarttravel.analyzer.domain.model.packagebundle;

import com.smarttravel.analyzer.domain.model.carrental.CarRentalOffer;
import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.util.ArrayList;
import java.util.List;

public record TravelPackage(String id, Money totalPrice, Score valueScore, Score qualityScore,
                            Score convenienceScore, PackageBundleType recommendationType,
                            PackagePart<FlightOffer> flight, PackagePart<LodgingOffer> lodging,
                            PackagePart<CarRentalOffer> car) implements Comparable<TravelPackage> {

    @Override public int compareTo(TravelPackage other) { return Double.compare(other.valueScore.value(), valueScore.value()); }

    public boolean meetsSmartBudgetFloor() { return qualityScore.value() >= 70 && convenienceScore.value() >= 60; }

    public boolean hasCar() { return car != null; }

    public Money advertisedPrice() {
        Money sum = flight.price().rawPrice().add(lodging.price().rawPrice());
        return hasCar() ? sum.add(car.price().rawPrice()) : sum;
    }

    /**
     * Quanto o anuncio escondeu. Uma fonte pode anunciar mais caro do que cobra (desconto),
     * e ai nao ha custo oculto nenhum: zero, nunca negativo, que o Money proibe.
     */
    public Money hiddenCosts() {
        var advertised = advertisedPrice();
        return totalPrice.compareTo(advertised) <= 0
            ? new Money(java.math.BigDecimal.ZERO, totalPrice.currency())
            : totalPrice.subtract(advertised);
    }

    public List<String> costAdjustments() {
        var all = new ArrayList<String>();
        addMissing(all, flight.price().includedCostAdjustments());
        addMissing(all, lodging.price().includedCostAdjustments());
        if (hasCar()) addMissing(all, car.price().includedCostAdjustments());
        return List.copyOf(all);
    }

    public TravelPackage withRecommendationType(PackageBundleType type) {
        return new TravelPackage(id, totalPrice, valueScore, qualityScore, convenienceScore, type, flight, lodging, car);
    }

    private static void addMissing(List<String> target, List<String> source) {
        source.stream().filter(item -> !target.contains(item)).forEach(target::add);
    }
}
