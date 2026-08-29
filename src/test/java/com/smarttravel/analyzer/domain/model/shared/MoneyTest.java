package com.smarttravel.analyzer.domain.model.shared;

import com.smarttravel.analyzer.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test void brlFactoryBuildsBrazilianCurrencyWithTwoDecimalPlaces() {
        var value = Money.brl("1234.5");
        assertThat(value.currency().getCurrencyCode()).isEqualTo("BRL");
        assertThat(value.amount()).isEqualByComparingTo("1234.50");
    }

    @Test void subtractReturnsTheDifferenceBetweenTwoAmounts() {
        assertThat(Money.brl("1850.00").subtract(Money.brl("1576.00")).amount())
            .isEqualByComparingTo("274.00");
    }

    @Test void subtractRejectsDifferentCurrencies() {
        assertThatThrownBy(() -> Money.brl("100.00").subtract(Money.of("100.00", "USD")))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("Currency mismatch");
    }
}
