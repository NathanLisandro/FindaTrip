package com.smarttravel.analyzer.infrastructure.configuration;

import com.smarttravel.analyzer.domain.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {
    @Bean PriceTrendDomainService priceTrendDomainService() { return new PriceTrendDomainService(); }
    @Bean PackageBundlerDomainService packageBundlerDomainService() { return new PackageBundlerDomainService(); }
    @Bean ValueScoringDomainService valueScoringDomainService() { return new ValueScoringDomainService(); }
    @Bean CostNormalizerDomainService costNormalizerDomainService() { return new CostNormalizerDomainService(); }
}
