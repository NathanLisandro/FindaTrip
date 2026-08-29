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
    /** Nome como a pagina escreve -> codigo IATA, que e o que identifica o logo. */
    private static final java.util.Map<String, String> AIRLINES = new java.util.LinkedHashMap<>(java.util.Map.of(
        "LATAM", "LA", "GOL", "G3", "Azul", "AD", "Voepass", "2Z"));
    private static final Pattern STOPS = Pattern.compile("(\\d+)\\s+parada");
    /** "1 parada Parada de 5h 55 min" e tambem "1 parada 55 min", que a pagina usa nas escalas curtas. */
    private static final Pattern LAYOVER =
        Pattern.compile("parada[s]?\\s+(?:Parada de\\s+)?(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*min)");
    /** "1 parada em CGH" — o codigo do aeroporto de conexao. */
    private static final Pattern HUB = Pattern.compile("parada[s]?\\s+em\\s+([A-Z]{3})");
    /** A pagina rotula os horarios: "Horario de partida: 06:20." */
    private static final Pattern PARTIDA = Pattern.compile("Hor[aá]rio de partida:\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern CHEGADA = Pattern.compile("Hor[aá]rio de chegada:\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern DURACAO = Pattern.compile("Dura[cç][aã]o total:\\s*(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*min)?");
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

        // A pagina anuncia por adulto ("os precos incluem os tributos e tarifas obrigatorios
        // para 1 adulto"), entao a tarifa do grupo e a tarifa vezes o numero de viajantes.
        // Sem isto o voo de duas pessoas entrava pela metade do preco no pacote.
        var grupo = fare.get().multiply(criteria.travelers());

        // A pagina nao separa tributos de aeroporto nem bagagem despachada: ficam zero,
        // e inventar numero seria pior que admitir que nao sabemos.
        var rotulos = card.select("[aria-label]").eachAttr("aria-label").toString();
        return Optional.of(new FlightOffer("google-flights-" + index, airline(text), grupo,
            ZERO, ZERO, legs(text, criteria), 0.9,
            hora(PARTIDA, rotulos), hora(CHEGADA, rotulos), duracao(rotulos)));
    }

    private static java.time.LocalTime hora(Pattern padrao, String rotulos) {
        var m = padrao.matcher(rotulos);
        if (!m.find()) return null;
        return java.time.LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    private static java.time.Duration duracao(String rotulos) {
        var m = DURACAO.matcher(rotulos);
        if (!m.find()) return null;
        long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
        long min = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
        return (h == 0 && min == 0) ? null : java.time.Duration.ofHours(h).plusMinutes(min);
    }

    private Airline airline(String text) {
        var minusculo = text.toLowerCase(Locale.ROOT);
        for (var entrada : AIRLINES.entrySet()) {
            if (minusculo.contains(entrada.getKey().toLowerCase(Locale.ROOT))) {
                return new Airline(entrada.getValue(), entrada.getKey());
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
