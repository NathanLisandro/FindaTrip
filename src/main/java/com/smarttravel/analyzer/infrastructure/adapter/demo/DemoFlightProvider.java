package com.smarttravel.analyzer.infrastructure.adapter.demo;

import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.repository.FlightProviderPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DemoFlightProvider implements FlightProviderPort {

    private static final List<Airline> AIRLINES = List.of(
        new Airline("G3", "GOL"), new Airline("AD", "Azul"), new Airline("LA", "LATAM"));

    @Override public boolean isDemo() { return true; }

    @Override public List<FlightOffer> searchFlights(SearchCriteria criteria) {
        var random = new Random(criteria.hashCode());
        var offers = new ArrayList<FlightOffer>();
        for (int index = 0; index < 6; index++) {
            var airline = AIRLINES.get(index % AIRLINES.size());
            boolean direct = index % 2 == 0;
            var fare = money(420 + random.nextInt(680));
            offers.add(new FlightOffer("demo-fl-" + index, airline, fare, money(60 + random.nextInt(60)),
                money(70 + random.nextInt(50)), legs(criteria, direct), 0.75 + random.nextDouble() * 0.24));
        }
        return List.copyOf(offers);
    }

    private static List<FlightLeg> legs(SearchCriteria criteria, boolean direct) {
        var origin = new Location(criteria.origin(), criteria.origin(), "BR");
        var destination = new Location(criteria.destination(), criteria.destination(), "BR");
        if (direct) return List.of(new FlightLeg(origin, destination, null, false, false));
        var hub = new Location("GRU", "São Paulo", "BR");
        return List.of(new FlightLeg(origin, hub, Duration.ofMinutes(110), false, false),
                       new FlightLeg(hub, destination, null, false, false));
    }

    private static Money money(int amount) { return new Money(BigDecimal.valueOf(amount), Money.BRL); }
}
