package com.smarttravel.analyzer.infrastructure.configuration;

import com.smarttravel.analyzer.domain.repository.*;
import com.smarttravel.analyzer.domain.service.*;
import com.smarttravel.analyzer.infrastructure.adapter.demo.*;
import com.smarttravel.analyzer.infrastructure.adapter.scraper.*;
import java.nio.file.Path;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class BeanConfig {
    @Bean PriceTrendDomainService priceTrendDomainService() { return new PriceTrendDomainService(); }
    @Bean PackageBundlerDomainService packageBundlerDomainService() { return new PackageBundlerDomainService(); }
    @Bean ValueScoringDomainService valueScoringDomainService() { return new ValueScoringDomainService(); }
    @Bean CostNormalizerDomainService costNormalizerDomainService() { return new CostNormalizerDomainService(); }
    @Bean PackageFilterDomainService packageFilterDomainService() { return new PackageFilterDomainService(); }
    @Bean PackageExplanationDomainService packageExplanationDomainService() { return new PackageExplanationDomainService(); }

    @Bean java.util.concurrent.Executor searchExecutor() {
        var executor = new org.springframework.core.task.SimpleAsyncTaskExecutor("busca-");
        executor.setConcurrencyLimit(4);
        return executor;
    }

    private static final Path SCRAPERS = Path.of("scrapers");

    /**
     * Hospedagem vem de scraper real. O composite existe para as proximas fontes
     * (Google Hotels, Airbnb) entrarem sem tocar em dominio nem aplicacao.
     */
    @Profile("!test")
    @Bean LodgingProviderPort lodgingProvider(PageFetcherPort fetcher, SiteConfigLoader loader) {
        var configs = loader.load(SCRAPERS);
        return new CompositeLodgingProvider(List.of(new BookingScraper(fetcher, configs.get("booking"))));
    }

    @Profile("!test")
    @Bean FlightProviderPort flightProvider(PageFetcherPort fetcher, SiteConfigLoader loader) {
        return new GoogleFlightsScraper(fetcher, loader.load(SCRAPERS).get("google-flights"));
    }

    /** Nenhuma locadora foi raspada ainda: o carro segue simulado, e a tela avisa por fonte. */
    @Bean CarRentalProviderPort carRentalProvider() { return new DemoCarRentalProvider(); }

    /**
     * No perfil de teste as fontes sao simuladas. Sem isto o @SpringBootTest sobe o Chrome
     * e bate nos sites de verdade: teste lento, instavel e refem do anti-bot.
     */
    @Profile("test")
    @Bean LodgingProviderPort demoLodgingProvider() { return new DemoLodgingProvider(); }

    @Profile("test")
    @Bean FlightProviderPort demoFlightProvider() { return new DemoFlightProvider(); }

    @Bean PackageAssemblerDomainService packageAssemblerDomainService(CostNormalizerDomainService normalizer, ValueScoringDomainService scoring, PriceTrendDomainService trends) {
        return new PackageAssemblerDomainService(normalizer, scoring, trends);
    }
}
