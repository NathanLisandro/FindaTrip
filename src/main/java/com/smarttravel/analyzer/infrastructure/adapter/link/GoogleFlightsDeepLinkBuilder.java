package com.smarttravel.analyzer.infrastructure.adapter.link;

import com.smarttravel.analyzer.domain.link.DeepLinkBuilder;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class GoogleFlightsDeepLinkBuilder implements DeepLinkBuilder {

    @Override public String partnerName() { return "Google Flights"; }

    @Override public URI searchUrl(SearchCriteria criteria) {
        var query = "Flights from " + criteria.origin() + " to " + criteria.destination()
            + " on " + criteria.departureDate() + " through " + criteria.returnDate();
        return URI.create("https://www.google.com/travel/flights?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }
}
