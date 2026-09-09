package com.engine.order.domain.model;

import com.engine.order.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("Should create Money and normalize scale to 2 decimal places")
    void shouldCreateMoneyWithNormalizedScale() {
        Money money = Money.of(new BigDecimal("99.991"), Currency.getInstance("USD"));
        assertThat(money.amount()).isEqualTo(new BigDecimal("99.99"));
        assertThat(money.currency().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Should throw exception when amount is negative")
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-10.00"), Currency.getInstance("USD")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("Should add amounts with matching currency")
    void shouldAddSameCurrency() {
        Money m1 = Money.of(10.50, "USD");
        Money m2 = Money.of(5.25, "USD");
        Money sum = m1.add(m2);

        assertThat(sum.amount()).isEqualTo(new BigDecimal("15.75"));
        assertThat(sum.currency().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Should fail adding amounts with different currencies")
    void shouldFailAddMismatchedCurrency() {
        Money usd = Money.of(10.00, "USD");
        Money eur = Money.of(10.00, "EUR");

        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    @DisplayName("Should multiply amount by factor")
    void shouldMultiplyCorrectly() {
        Money m = Money.of(12.50, "EUR");
        Money result = m.multiply(3);

        assertThat(result.amount()).isEqualTo(new BigDecimal("37.50"));
    }

    @Test
    @DisplayName("Should fail multiplying by negative factor")
    void shouldFailMultiplyNegativeFactor() {
        Money m = Money.of(10.00, "USD");

        assertThatThrownBy(() -> m.multiply(-1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("Should compare correctly")
    void shouldCompareCorrectly() {
        Money low = Money.of(5.00, "USD");
        Money high = Money.of(10.00, "USD");

        assertThat(high.isGreaterThan(low)).isTrue();
        assertThat(low.isGreaterThan(high)).isFalse();
        assertThat(Money.zero("USD").isZero()).isTrue();
    }
}
