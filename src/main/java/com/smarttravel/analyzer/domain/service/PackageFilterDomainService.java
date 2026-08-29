package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.lodging.Amenity;
import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import com.smarttravel.analyzer.domain.model.search.NeighborhoodSummary;
import com.smarttravel.analyzer.domain.model.search.PackageFilter;
import com.smarttravel.analyzer.domain.model.shared.Money;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PackageFilterDomainService {

    public List<TravelPackage> apply(List<TravelPackage> packages, PackageFilter filter) {
        return packages.stream().filter(candidate -> matches(candidate, filter)).toList();
    }

    /** Bairros presentes nos pacotes, do mais barato para o mais caro. Nada é cadastrado por cidade. */
    public List<NeighborhoodSummary> summarise(List<TravelPackage> packages) {
        Map<String, List<TravelPackage>> grouped = new LinkedHashMap<>();
        for (var candidate : packages) {
            grouped.computeIfAbsent(candidate.lodging().offer().neighborhood(), key -> new java.util.ArrayList<>()).add(candidate);
        }
        return grouped.entrySet().stream()
            .map(entry -> new NeighborhoodSummary(entry.getKey(), entry.getValue().size(), cheapest(entry.getValue())))
            .sorted(Comparator.comparing(NeighborhoodSummary::cheapest))
            .toList();
    }

    private static Money cheapest(List<TravelPackage> packages) {
        return packages.stream().map(TravelPackage::totalPrice).min(Comparator.naturalOrder()).orElseThrow();
    }

    /** Compara sem acento e sem caixa: "boa viagem" acha "Boa Viagem". */
    static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean matches(TravelPackage candidate, PackageFilter filter) {
        if (filter.maxPrice() != null && candidate.totalPrice().amount().compareTo(filter.maxPrice()) > 0) return false;
        if (filter.minRating() != null && candidate.lodging().offer().rating().average() < filter.minRating()) return false;
        if (filter.directFlightOnly() && candidate.flight().offer().stops() > 0) return false;
        if (filter.breakfastIncluded() && !candidate.lodging().offer().has(Amenity.BREAKFAST_INCLUDED)) return false;
        // Conjunto vazio quer dizer "qualquer tipo", nao "nenhum tipo".
        if (!filter.stayTypes().isEmpty() && !filter.stayTypes().contains(candidate.lodging().offer().type())) return false;
        if (filter.neighborhood() != null && !filter.neighborhood().isBlank()
            && !fold(candidate.lodging().offer().neighborhood()).equals(fold(filter.neighborhood()))) return false;
        return !filter.freeCancellation() || candidate.lodging().offer().has(Amenity.FREE_FLEXIBLE_CANCELLATION);
    }
}
