package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.lodging.LodgingOffer;
import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import com.smarttravel.analyzer.domain.repository.LodgingProviderPort;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Junta varias fontes de hospedagem atras de uma porta so.
 * Uma fonte que cai nao impede as outras: o buscador continua com o que conseguiu.
 */
public class CompositeLodgingProvider implements LodgingProviderPort {

    private static final Logger log = LoggerFactory.getLogger(CompositeLodgingProvider.class);

    private final List<LodgingProviderPort> sources;

    public CompositeLodgingProvider(List<LodgingProviderPort> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override public List<LodgingOffer> searchLodging(SearchCriteria criteria) {
        var offers = new ArrayList<LodgingOffer>();
        for (var source : sources) {
            try {
                offers.addAll(source.searchLodging(criteria));
            } catch (RuntimeException failure) {
                log.warn("Fonte de hospedagem {} falhou: {}", source.getClass().getSimpleName(), failure.getMessage());
            }
        }
        return List.copyOf(offers);
    }

    /** So e demonstracao quando NENHUMA fonte traz dado real. */
    @Override public boolean isDemo() {
        return !sources.isEmpty() && sources.stream().allMatch(LodgingProviderPort::isDemo);
    }
}
