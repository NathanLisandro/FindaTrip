package com.smarttravel.analyzer.domain.service;

import com.smarttravel.analyzer.domain.model.packagebundle.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PackageBundlerDomainService {

    private static final Comparator<TravelPackage> BY_VALUE =
        Comparator.comparingDouble(candidate -> candidate.valueScore().value());
    private static final Comparator<TravelPackage> BY_COMFORT =
        Comparator.comparingDouble(candidate -> candidate.qualityScore().value() + candidate.convenienceScore().value());

    /**
     * Devolve ate tres recomendacoes distintas, uma por perfil.
     * O mesmo pacote costuma vencer em mais de um perfil; mostrar o mesmo card tres vezes com
     * rotulos diferentes engana. Entao cada perfil leva o melhor pacote que ainda nao foi escolhido.
     */
    public List<TravelPackage> topRecommendations(List<TravelPackage> candidates) {
        var chosen = new ArrayList<TravelPackage>();
        pick(candidates, chosen, PackageBundleType.BEST_VALUE_OVERALL,
            remaining -> remaining.stream().max(BY_VALUE));
        pick(candidates, chosen, PackageBundleType.SMART_BUDGET,
            remaining -> remaining.stream().filter(TravelPackage::meetsSmartBudgetFloor).min(Comparator.comparing(TravelPackage::totalPrice))
                .or(() -> remaining.stream().min(Comparator.comparing(TravelPackage::totalPrice))));
        pick(candidates, chosen, PackageBundleType.MAX_COMFORT,
            remaining -> remaining.stream().max(BY_COMFORT));
        return List.copyOf(chosen);
    }

    private static void pick(List<TravelPackage> candidates, List<TravelPackage> chosen,
                             PackageBundleType type, java.util.function.Function<List<TravelPackage>, Optional<TravelPackage>> winner) {
        var remaining = candidates.stream().filter(candidate -> isNew(chosen, candidate)).toList();
        winner.apply(remaining).ifPresent(pick -> chosen.add(pick.withRecommendationType(type)));
    }

    /**
     * Um perfil so leva um pacote cuja HOSPEDAGEM ainda nao foi escolhida.
     * Tres cards com o mesmo hotel nao sao tres opcoes: sao um card repetido tres vezes,
     * ainda que o voo mude. A hospedagem e o que o usuario reconhece na tela.
     */
    private static boolean isNew(List<TravelPackage> chosen, TravelPackage candidate) {
        return chosen.stream().noneMatch(taken ->
            taken.id().equals(candidate.id())
                || taken.lodging().offer().id().equals(candidate.lodging().offer().id()));
    }
}
