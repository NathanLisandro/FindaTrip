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
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class AirbnbScraper implements LodgingProviderPort {

    private static final String SITE = "airbnb";
    private static final Path SCRAPERS = Path.of("scrapers");

    /** "6x R$ 461" e parcelamento, nao diaria: some antes de procurar o total. */
    private static final Pattern INSTALLMENT = Pattern.compile("\\d+\\s*x\\s*R\\$\\s*[\\d.,]+");
    /** "4,92 (88)" — nota e numero de avaliacoes. */
    private static final Pattern RATING = Pattern.compile("(\\d+(?:,\\d+)?)\\s*\\((\\d+)\\)");
    private static final Money ZERO = new Money(BigDecimal.ZERO, Money.BRL);

    private final PageFetcherPort fetcher;
    private final SiteConfig config;

    public AirbnbScraper(PageFetcherPort fetcher, SiteConfig config) {
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
        var name = text(card, config.selector("nome"));
        if (name == null || name.isBlank()) return Optional.empty();

        var body = card.text();
        // Sem o parcelamento, o ultimo valor e o total ja com desconto ("Total: R$ 3.116 R$ 2.766").
        var total = BrazilianText.money(INSTALLMENT.matcher(body).replaceAll(" "));
        if (total.isEmpty()) return Optional.empty();

        var amenities = EnumSet.noneOf(Amenity.class);
        if (body.contains("Cancelamento gratuito")) amenities.add(Amenity.FREE_FLEXIBLE_CANCELLATION);
        if (body.contains("Café da manhã")) amenities.add(Amenity.BREAKFAST_INCLUDED);


        // O Airbnb nao publica bairro no card: passar null vira "Nao informado" no dominio,
        // que e a verdade. Deduzir bairro do titulo seria chute.
        return Optional.of(new LodgingOffer("airbnb-" + index, name, null, total.get(), nights,
            ZERO, ZERO, ZERO, rating(body), Set.copyOf(amenities), 0));
    }

    /** Anuncio novo nao tem nota. Zero avaliacoes e o que o rating bayesiano ja sabe tratar. */
    private static HotelRating rating(String body) {
        var matcher = RATING.matcher(body);
        if (!matcher.find()) return new HotelRating(0, 0);
        return new HotelRating(Double.parseDouble(matcher.group(1).replace(',', '.')),
                               Integer.parseInt(matcher.group(2)));
    }

    private static String text(Element card, String selector) {
        var found = card.selectFirst(selector);
        return found == null ? null : found.text();
    }
}
