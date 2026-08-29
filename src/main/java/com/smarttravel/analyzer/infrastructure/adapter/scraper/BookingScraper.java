package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.lodging.*;
import com.smarttravel.analyzer.domain.model.scraper.SiteConfig;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.repository.LodgingProviderPort;
import com.smarttravel.analyzer.domain.repository.PageFetcherPort;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class BookingScraper implements LodgingProviderPort {

    private static final String SOURCE = "Booking.com";

    private static final String SITE = "booking";
    private static final Path SCRAPERS = Path.of("scrapers");

    private final PageFetcherPort fetcher;
    private final SiteConfig config;

    public BookingScraper(PageFetcherPort fetcher, SiteConfig config) {
        this.fetcher = fetcher;
        this.config = config == null ? new SiteConfigLoader().load(SCRAPERS).get(SITE) : config;
    }

    @Override public boolean isDemo() { return false; }

    @Override public List<LodgingOffer> searchLodging(SearchCriteria criteria) {
        var url = config.url(Map.of(
            "cidade", criteria.destination(),
            "entrada", criteria.departureDate().toString(),
            "saida", criteria.returnDate().toString(),
            "adultos", String.valueOf(criteria.travelers())));
        return parse(fetcher.fetch(url, config.waitFor(), Duration.ofSeconds(45)), criteria);
    }

    /** Publico de proposito: o teste roda o parser contra HTML gravado, sem rede. */
    public List<LodgingOffer> parse(String html, SearchCriteria criteria) {
        int nights = Math.max(1, (int) ChronoUnit.DAYS.between(criteria.departureDate(), criteria.returnDate()));
        var offers = new ArrayList<LodgingOffer>();
        var cards = Jsoup.parse(html).select(config.selector("card"));
        for (int index = 0; index < cards.size(); index++) {
            toOffer(cards.get(index), index, nights).ifPresent(offers::add);
        }
        return List.copyOf(offers);
    }

    private Optional<LodgingOffer> toOffer(Element card, int index, int nights) {
        var total = BrazilianText.money(text(card, "preco"));
        if (total.isEmpty()) return Optional.empty();   // card sem preco nao derruba a lista

        var name = text(card, "nome");
        if (name == null || name.isBlank()) return Optional.empty();

        var score = text(card, "nota");
        var rating = new HotelRating(BrazilianText.decimal(score).orElse(0), BrazilianText.integer(score).orElse(0));

        var body = card.text();
        var amenities = EnumSet.noneOf(Amenity.class);
        if (body.contains("Café da manhã incluído")) amenities.add(Amenity.BREAKFAST_INCLUDED);
        if (body.contains("Cancelamento grátis")) amenities.add(Amenity.FREE_FLEXIBLE_CANCELLATION);

        var taxes = BrazilianText.money(text(card, "taxas")).orElse(new Money(BigDecimal.ZERO, Money.BRL));
        var zero = new Money(BigDecimal.ZERO, Money.BRL);

        return Optional.of(new LodgingOffer("booking-" + index, name, neighborhood(card), total.get(), nights,
            taxes, zero, zero, rating, Set.copyOf(amenities),
            BrazilianText.decimal(text(card, "distancia")).orElse(0),
            SOURCE, link(card)));
    }

    /** "Campeche, Florianopolis" -> "Campeche". Sem virgula, nao ha bairro. */
    private String neighborhood(Element card) {
        var address = text(card, "endereco");
        if (address == null) return null;
        int comma = address.indexOf(',');
        return comma > 0 ? address.substring(0, comma).trim() : null;
    }

    /**
     * O link do proprio anuncio, que o card ja traz.
     * Mandar o usuario para a pagina de busca faria ele procurar de novo o que ja achamos —
     * e, com varias fontes, levaria ao site errado.
     */
    private String link(Element card) {
        var anchor = card.selectFirst(config.selector("link"));
        if (anchor == null) return null;
        var href = anchor.attr("href");
        if (href == null || href.isBlank()) return null;
        var semQuery = href.split("\\?")[0];
        return semQuery.startsWith("http") ? semQuery : "https://www.booking.com" + semQuery;
    }

    private String text(Element card, String selectorKey) {
        var found = card.selectFirst(config.selector(selectorKey));
        return found == null ? null : found.text();
    }
}
