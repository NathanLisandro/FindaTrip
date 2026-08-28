package com.smarttravel.analyzer.domain.model.flight;

import com.smarttravel.analyzer.domain.model.shared.Location;
import java.time.Duration;

public record FlightLeg(Location origin, Location destination, Duration layoverBeforeNextLeg, boolean overnightConnection, boolean requiresAirportChange) {
    public boolean hasLongLayover() { return layoverBeforeNextLeg != null && layoverBeforeNextLeg.compareTo(Duration.ofHours(5)) > 0; }
    public boolean hasSmartLayover() { return layoverBeforeNextLeg != null && !layoverBeforeNextLeg.minusHours(1).isNegative() && layoverBeforeNextLeg.compareTo(Duration.ofMinutes(150)) <= 0; }
}
