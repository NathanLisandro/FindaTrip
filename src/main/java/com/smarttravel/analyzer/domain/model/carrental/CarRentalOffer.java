package com.smarttravel.analyzer.domain.model.carrental;

import com.smarttravel.analyzer.domain.exception.DomainException;
import com.smarttravel.analyzer.domain.model.shared.*;
import java.util.List;

public record CarRentalOffer(String id, String supplier, CarCategory category, Money dailyRate, int rentalDays, InsuranceCoverageType includedCoverage, Money zeroDeductibleDailyFee, Money airportPickupFee, boolean unlimitedMileage, PickupMode pickupMode, double supplierReliabilityIndex) {
    public CarRentalOffer { if (!unlimitedMileage) throw new DomainException("Car rental comparison requires unlimited mileage"); }
    public NormalizedPrice normalizeWithFullInsurance() {
        Money raw = dailyRate.multiply(rentalDays);
        Money total = raw.add(airportPickupFee);
        var adjustments = new java.util.ArrayList<String>(List.of("airport_pickup_fee"));
        if (includedCoverage != InsuranceCoverageType.ZERO_DEDUCTIBLE_FULL_COVERAGE) { total = total.add(zeroDeductibleDailyFee.multiply(rentalDays)); adjustments.add("zero_deductible_full_coverage"); }
        return new NormalizedPrice(raw, total, adjustments);
    }
}
