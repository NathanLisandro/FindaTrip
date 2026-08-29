package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.scraper.SiteConfig;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.repository.FlightProviderPort;
import com.smarttravel.analyzer.domain.repository.PageFetcherPort;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class GoogleFlightsScraper implements FlightProviderPort {

    private static final String SITE = "google-flights";
    private static final Path SCRAPERS = Path.of("scrapers");

    /** A pagina escreve "Gol" e "LATAM"; o nome canonico e o desta lista. */
    private static final List<String> AIRLINES = List.of("LATAM", "GOL", "Azul", "Voepass");
    private static final Pattern STOPS = Pattern.compile("(\\d+)\\s+parada");
    /** "1 parada Parada de 5h 55 min" e tambem "1 parada 55 min", que a pagina usa nas escalas curtas. */
    private static final Pattern LAYOVER =
        Pattern.compile("parada[s]?\\s+(?:Parada de\\s+)?(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*min)");
    /** "1 parada em CGH" — o codigo do aeroporto de conexao. */
    private static final Pattern HUB = Pattern.compile("parada[s]?\\s+em\\s+([A-Z]{3})");
    private static final Money ZERO = new Money(BigDecimal.ZERO, Money.BRL);

    private final PageFetcherPort fetcher;
    private final SiteConfig config;

    public GoogleFlightsScraper(PageFetcherPort fetcher, SiteConfig config) {
        this.fetcher = fetcher;
        this.config = config == null ? new SiteConfigLoader().load(SCRAPERS).get(SITE) : config;
    }

    @Override public boolean isDemo() { return false; }

    @Override public List<FlightOffer> searchFlights(SearchCriteria criteria) {
        var url = config.url(Map.of(
            "origem", criteria.origin(),
            "destino", criteria.destination(),
            "ida", criteria.departureDate().toString(),
            "volta", criteria.returnDate().toString()));
        return parse(fetcher.fetch(url, config.waitFor(), Duration.ofSeconds(45)), criteria);
    }

    /** Publico de proposito: o teste roda o parser contra HTML gravado, sem rede. */
    public List<FlightOffer> parse(String html, SearchCriteria criteria) {
        var offers = new ArrayList<FlightOffer>();
        // A pagina lista os mesmos voos em "Principais voos de ida" e "Outros voos de ida".
        // Sem isto o mesmo voo contaria duas vezes entre os cinco mais baratos da montagem.
        var seen = new HashSet<String>();
        var cards = Jsoup.parse(html).select(config.selector("card"));
        for (int index = 0; index < cards.size(); index++) {
            toOffer(cards.get(index), index, criteria)
                .filter(offer -> seen.add(signature(offer)))
                .ifPresent(offers::add);
        }
        return List.copyOf(offers);
    }

    private static String signature(FlightOffer offer) {
        var first = offer.legs().getFirst();
        return offer.airline().name() + "|" + offer.rawFare().amount() + "|" + offer.stops()
            + "|" + first.destination().code() + "|" + first.layoverBeforeNextLeg();
    }

    private Optional<FlightOffer> toOffer(Element card, int index, SearchCriteria criteria) {
        var text = card.text();
        var fare = BrazilianText.firstMoney(text);
        if (fare.isEmpty()) return Optional.empty();   // item sem preco nao derruba a lista

        // A pagina nao separa tributos de aeroporto nem bagagem despachada: ficam zero,
        // e inventar numero seria pior que admitir que nao sabemos.
        return Optional.of(new FlightOffer("google-flights-" + index, airline(text), fare.get(),
            ZERO, ZERO, legs(text, criteria), 0.9));
    }

    private Airline airline(String text) {
        for (var name : AIRLINES) {
            if (text.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
                return new Airline(name.toUpperCase(Locale.ROOT), name);
            }
        }
        return new Airline("--", "Outra");
    }

    private List<FlightLeg> legs(String text, SearchCriteria criteria) {
        var origin = new Location(criteria.origin(), null, null);
        var destination = new Location(criteria.destination(), null, null);
        boolean overnight = text.contains("Pernoite");
        boolean airportChange = text.contains("Troca de aeroporto");

        if (stops(text) == 0) {
            return List.of(new FlightLeg(origin, destination, null, overnight, airportChange));
        }
        var hub = new Location(hub(text), null, null);
        return List.of(
            new FlightLeg(origin, hub, layover(text), overnight, airportChange),
            new FlightLeg(hub, destination, null, false, false));
    }

    private int stops(String text) {
        if (text.contains("Sem escalas") || text.contains("Direto")) return 0;
        var matcher = STOPS.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private Duration layover(String text) {
        var matcher = LAYOVER.matcher(text);
        if (!matcher.find()) return null;
        long hours = matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1));
        long minutes = matcher.group(2) == null ? 0 : Long.parseLong(matcher.group(2));
        return Duration.ofHours(hours).plusMinutes(minutes);
    }

    private String hub(String text) {
        var matcher = HUB.matcher(text);
        return matcher.find() ? matcher.group(1) : "---";
    }
}
