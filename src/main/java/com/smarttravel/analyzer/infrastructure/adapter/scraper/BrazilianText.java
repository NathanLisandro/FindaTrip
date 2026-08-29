package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.shared.Money;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/** Numeros como os sites brasileiros escrevem: "R$ 1.540", "7,7", "1.238 avaliacoes". */
public final class BrazilianText {

    private static final Pattern MONEY = Pattern.compile("R\\$\\s*([\\d.]+(?:,\\d{2})?)");
    private static final Pattern DECIMAL = Pattern.compile("(\\d+,\\d+)");
    /** Um inteiro inteiro: "700", "1.238". O que vem de um decimal ("7,7") nao conta. */
    private static final Pattern INTEGER = Pattern.compile("(?<![\\d,.])(\\d{1,3}(?:\\.\\d{3})*)(?![\\d,])");

    /** O Booking separa "R$" do numero com espaco nao separavel (U+00A0), que \s nao casa. */
    private static final char NBSP = ' ';

    private BrazilianText() {}

    /** O ULTIMO valor da string: o Booking mostra "R$ 1.540 R$ 893", e 893 e o que se paga. */
    public static Optional<Money> money(String text) {
        if (text == null) return Optional.empty();
        var matcher = MONEY.matcher(normalize(text));
        String last = null;
        while (matcher.find()) last = matcher.group(1);
        if (last == null) return Optional.empty();
        return Optional.of(new Money(toNumber(last), Money.BRL));
    }

    public static Optional<Money> firstMoney(String text) {
        if (text == null) return Optional.empty();
        var matcher = MONEY.matcher(normalize(text));
        if (!matcher.find()) return Optional.empty();
        return Optional.of(new Money(toNumber(matcher.group(1)), Money.BRL));
    }

    public static OptionalDouble decimal(String text) {
        if (text == null) return OptionalDouble.empty();
        var matcher = DECIMAL.matcher(normalize(text));
        if (!matcher.find()) return OptionalDouble.empty();
        return OptionalDouble.of(Double.parseDouble(matcher.group(1).replace(",", ".")));
    }

    public static OptionalInt integer(String text) {
        if (text == null) return OptionalInt.empty();
        var matcher = INTEGER.matcher(normalize(text));
        while (matcher.find()) {
            var raw = matcher.group(1).replace(".", "");
            if (!raw.isEmpty() && raw.length() <= 9) return OptionalInt.of(Integer.parseInt(raw));
        }
        return OptionalInt.empty();
    }

    private static String normalize(String text) {
        return text.replace(NBSP, ' ');
    }

    private static BigDecimal toNumber(String raw) {
        return new BigDecimal(raw.replace(".", "").replace(",", "."));
    }
}
