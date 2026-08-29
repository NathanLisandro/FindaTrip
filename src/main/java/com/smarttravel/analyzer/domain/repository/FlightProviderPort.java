package com.smarttravel.analyzer.domain.repository;
import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.util.List;
public interface FlightProviderPort {
    List<FlightOffer> searchFlights(SearchCriteria criteria);
    default boolean isDemo() { return false; }
}
