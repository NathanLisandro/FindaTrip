package com.smarttravel.analyzer.domain.model.shared;

import java.util.List;

public record NormalizedPrice(Money rawPrice, Money totalPrice, List<String> includedCostAdjustments) {
    public NormalizedPrice { includedCostAdjustments = List.copyOf(includedCostAdjustments); }
    public boolean includes(String adjustment) { return includedCostAdjustments.contains(adjustment); }
}
