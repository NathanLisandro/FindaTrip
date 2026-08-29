package com.smarttravel.analyzer.domain.model.search;

import com.smarttravel.analyzer.domain.model.carrental.CarRentalOffer;
import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import java.util.List;

public record TravelOffers(List<FlightOffer> flights, List<LodgingOffer> lodgings, List<CarRentalOffer> cars) {
    public TravelOffers {
        flights = List.copyOf(flights);
        lodgings = List.copyOf(lodgings);
        cars = List.copyOf(cars);
    }
    public static TravelOffers empty() { return new TravelOffers(List.of(), List.of(), List.of()); }
}
