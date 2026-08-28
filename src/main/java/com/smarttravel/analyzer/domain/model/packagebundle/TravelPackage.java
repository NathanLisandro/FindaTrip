package com.smarttravel.analyzer.domain.model.packagebundle;

import com.smarttravel.analyzer.domain.model.shared.*;

public record TravelPackage(String id, Money totalPrice, Score valueScore, Score qualityScore, Score convenienceScore, PackageBundleType recommendationType) implements Comparable<TravelPackage> {
    @Override public int compareTo(TravelPackage other) { return Double.compare(other.valueScore.value(), valueScore.value()); }
    public boolean meetsSmartBudgetFloor() { return qualityScore.value() >= 70 && convenienceScore.value() >= 60; }
}
