package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.flight.FlightOffer;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import com.smarttravel.analyzer.domain.repository.FlightProviderPort;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Junta varias fontes de voo. Uma que cai nao impede as outras. */
public class CompositeFlightProvider implements FlightProviderPort {

    private static final Logger log = LoggerFactory.getLogger(CompositeFlightProvider.class);

    private final List<FlightProviderPort> sources;

    public CompositeFlightProvider(List<FlightProviderPort> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override public List<FlightOffer> searchFlights(SearchCriteria criteria) {
        var offers = new ArrayList<FlightOffer>();
        for (var source : sources) {
            try {
                offers.addAll(source.searchFlights(criteria));
            } catch (RuntimeException failure) {
                log.warn("Fonte de voo {} falhou: {}", source.getClass().getSimpleName(), failure.getMessage());
            }
        }
        return List.copyOf(offers);
    }

    @Override public boolean isDemo() {
        return !sources.isEmpty() && sources.stream().allMatch(FlightProviderPort::isDemo);
    }
}
