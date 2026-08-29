package com.smarttravel.analyzer.infrastructure.adapter.link;

import com.smarttravel.analyzer.domain.link.DeepLinkBuilder;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class BookingDeepLinkBuilder implements DeepLinkBuilder {

    @Override public String partnerName() { return "Booking.com"; }

    @Override public URI searchUrl(SearchCriteria criteria) {
        return URI.create("https://www.booking.com/searchresults.html"
            + "?ss=" + encode(criteria.destination())
            + "&checkin=" + criteria.departureDate()
            + "&checkout=" + criteria.returnDate()
            + "&group_adults=" + criteria.travelers());
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
