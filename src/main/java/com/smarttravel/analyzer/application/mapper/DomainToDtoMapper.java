package com.smarttravel.analyzer.application.mapper;

import com.smarttravel.analyzer.application.dto.PackageResponseRecord;
import com.smarttravel.analyzer.domain.model.packagebundle.TravelPackage;
import org.springframework.stereotype.Component;

@Component
public class DomainToDtoMapper {
    public PackageResponseRecord toResponse(TravelPackage p) { return new PackageResponseRecord(p.id(), p.totalPrice().amount(), p.totalPrice().currency().getCurrencyCode(), p.valueScore().value(), p.recommendationType()); }
}
