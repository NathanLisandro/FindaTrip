package com.smarttravel.analyzer.domain.model.flight;

import com.smarttravel.analyzer.domain.model.shared.*;
import java.util.List;

public record FlightOffer(String id, Airline airline, Money rawFare, Money mandatoryAirportTaxes,
                          Money checkedBagFee, List<FlightLeg> legs, double reliabilityIndex,
                          java.time.LocalTime departureTime, java.time.LocalTime arrivalTime,
                          java.time.Duration totalDuration) {
    public FlightOffer { legs = List.copyOf(legs); }
    public NormalizedPrice normalize(boolean checkedBagRequested) {
        Money total = rawFare.add(mandatoryAirportTaxes);
        var adjustments = new java.util.ArrayList<String>();
        adjustments.add("mandatory_airport_taxes");
        if (checkedBagRequested) { total = total.add(checkedBagFee); adjustments.add("checked_baggage"); }
        return new NormalizedPrice(rawFare, total, adjustments);
    }
    public boolean hasAirportChangeConnection() { return legs.stream().anyMatch(FlightLeg::requiresAirportChange); }
    public int stops() { return Math.max(0, legs.size() - 1); }
}
