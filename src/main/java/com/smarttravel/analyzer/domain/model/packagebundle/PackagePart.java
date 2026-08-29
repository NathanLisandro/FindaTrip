package com.smarttravel.analyzer.domain.model.packagebundle;

import com.smarttravel.analyzer.domain.exception.DomainException;
import com.smarttravel.analyzer.domain.model.shared.NormalizedPrice;

public record PackagePart<T>(T offer, NormalizedPrice price) {
    public PackagePart {
        if (offer == null || price == null) throw new DomainException("Package part requires an offer and its normalized price");
    }
}
