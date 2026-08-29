package com.smarttravel.analyzer.domain.model.scraper;

import com.smarttravel.analyzer.domain.exception.DomainException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Pattern;

public record SiteConfig(String name, String searchUrl, String waitFor,
                         Map<String, String> selectors, int rateLimitMs, LocalDate verifiedOn,
                         String entryUrl) {

    /** Alguns sites so servem a busca para quem chega navegando de outra pagina. */
    public boolean needsEntryPage() { return entryUrl != null && !entryUrl.isBlank(); }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_]+)\\}");
    private static final int MAX_AGE_DAYS = 90;

    public SiteConfig {
        if (name == null || name.isBlank()) throw new DomainException("Site config requires a name");
        if (searchUrl == null || searchUrl.isBlank()) throw new DomainException("Site config " + name + " requires url_busca");
        selectors = Map.copyOf(selectors);
    }

    public String url(Map<String, String> values) {
        var filled = PLACEHOLDER.matcher(searchUrl).replaceAll(match -> {
            var key = match.group(1);
            var value = values.get(key);
            if (value == null) throw new DomainException("Faltou o valor de {" + key + "} para montar a URL de " + name);
            return java.util.regex.Matcher.quoteReplacement(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return filled;
    }

    public String selector(String key) {
        var value = selectors.get(key);
        if (value == null) throw new DomainException("scrapers/" + name + ".yml nao define o seletor '" + key + "'");
        return value;
    }

    public boolean isStale(LocalDate today) {
        return verifiedOn == null || ChronoUnit.DAYS.between(verifiedOn, today) > MAX_AGE_DAYS;
    }
}
