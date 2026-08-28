package com.smarttravel.analyzer.domain.model.shared;

public record Score(double value) {
    public Score { value = Math.max(0, Math.min(100, value)); }
    public static Score weighted(Score price, double wp, Score quality, double wq, Score convenience, double wc) {
        return new Score(price.value * wp + quality.value * wq + convenience.value * wc);
    }
}
