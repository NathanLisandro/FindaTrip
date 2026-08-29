package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import com.smarttravel.analyzer.domain.repository.LodgingProviderPort;
import com.smarttravel.analyzer.infrastructure.adapter.demo.DemoLodgingProvider;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeLodgingProviderTest {

    private static final SearchCriteria CRITERIA =
        new SearchCriteria("MGF", "Florianópolis", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), 2, true, false);

    private static final LodgingProviderPort BROKEN = criteria -> { throw new IllegalStateException("fonte fora do ar"); };

    @Test void joinsTheOffersOfEverySource() {
        var composite = new CompositeLodgingProvider(List.of(new DemoLodgingProvider(), new DemoLodgingProvider()));
        assertThat(composite.searchLodging(CRITERIA)).hasSize(12);
    }

    @Test void aSourceThatFailsDoesNotStopTheOthers() {
        var composite = new CompositeLodgingProvider(List.of(BROKEN, new DemoLodgingProvider()));
        assertThat(composite.searchLodging(CRITERIA)).hasSize(6);
    }

    @Test void everySourceFailingYieldsAnEmptyListNotAnException() {
        var composite = new CompositeLodgingProvider(List.of(BROKEN, BROKEN));
        assertThat(composite.searchLodging(CRITERIA)).isEmpty();
    }

    @Test void itIsOnlyDemoWhenEverySourceIsDemo() {
        assertThat(new CompositeLodgingProvider(List.of(new DemoLodgingProvider())).isDemo()).isTrue();
        assertThat(new CompositeLodgingProvider(List.of(new DemoLodgingProvider(), realSource())).isDemo()).isFalse();
    }

    private static LodgingProviderPort realSource() {
        return new LodgingProviderPort() {
            @Override public List<LodgingOffer> searchLodging(SearchCriteria criteria) { return List.of(); }
            @Override public boolean isDemo() { return false; }
        };
    }
}
