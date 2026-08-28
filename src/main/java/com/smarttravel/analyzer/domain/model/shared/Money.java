package com.smarttravel.analyzer.domain.model.shared;

import com.smarttravel.analyzer.domain.exception.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {
    public Money {
        if (amount == null || currency == null) throw new DomainException("Money amount and currency are required");
        if (amount.signum() < 0) throw new DomainException("Money cannot be negative");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
    public static Money of(String amount, String currency) { return new Money(new BigDecimal(amount), Currency.getInstance(currency)); }
    public Money add(Money other) { requireSameCurrency(other); return new Money(amount.add(other.amount), currency); }
    public Money multiply(long factor) { return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency); }
    public double ratioTo(Money other) { requireSameCurrency(other); return amount.divide(other.amount, 8, RoundingMode.HALF_UP).doubleValue(); }
    public void requireSameCurrency(Money other) { if (!currency.equals(other.currency)) throw new DomainException("Currency mismatch"); }
    @Override public int compareTo(Money other) { requireSameCurrency(other); return amount.compareTo(other.amount); }
}
