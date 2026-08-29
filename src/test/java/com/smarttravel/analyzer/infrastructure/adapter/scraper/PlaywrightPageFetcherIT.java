package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("rede")
class PlaywrightPageFetcherIT {

    @Test void rendersJavaScriptSoThePriceIsInTheHtml() {
        var fetcher = new PlaywrightPageFetcher();
        try {
            var html = fetcher.fetch("https://www.booking.com/searchresults.html?ss=Florian%C3%B3polis"
                + "&checkin=2026-11-07&checkout=2026-11-14&group_adults=2&selected_currency=BRL",
                "[data-testid='property-card']", Duration.ofSeconds(45));
            assertThat(html).contains("property-card").contains("R$");
        } finally { fetcher.close(); }
    }
}
