package com.smarttravel.analyzer.infrastructure.adapter.scraper;

import com.smarttravel.analyzer.domain.model.flight.*;
import com.smarttravel.analyzer.domain.model.scraper.SiteConfig;
import com.smarttravel.analyzer.domain.model.shared.*;
import com.smarttravel.analyzer.domain.repository.FlightProviderPort;
import com.smarttravel.analyzer.domain.repository.PageFetcherPort;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/**
 * A Decolar mostra horario de saida e de chegada, e ainda diz se a bagagem despachada
 * esta inclusa — coisas que o Google Voos nao separa.
 */
public class DecolarFlightScraper implements FlightProviderPort {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DecolarFlightScraper.class);

    private static final String SITE = "decolar";
    private static final Path SCRAPERS = Path.of("scrapers");
    private static final Money ZERO = new Money(BigDecimal.ZERO, Money.BRL);

    /**
     * Sem \b no fim: a pagina escreve "MGF 16:35FLN 21:45", grudando a hora no aeroporto
     * seguinte. Com fronteira de palavra o 16:35 nao casava e a chegada virava a partida.
     */
    private static final Pattern HORA = Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})");
    private static final Pattern DURACAO = Pattern.compile("(\\d+)\\s*h\\s*(\\d+)?\\s*m");
    private static final Pattern PARADAS = Pattern.compile("(\\d+)\\s*parada");
    private static final Map<String, String> COMPANHIAS = new LinkedHashMap<>(Map.of(
        "LATAM", "LA", "GOL", "G3", "Azul", "AD", "Voepass", "2Z"));

    private final PageFetcherPort fetcher;
    private final SiteConfig config;

    public DecolarFlightScraper(PageFetcherPort fetcher, SiteConfig config) {
        this.fetcher = fetcher;
        this.config = config == null ? new SiteConfigLoader().load(SCRAPERS).get(SITE) : config;
    }

    @Override public boolean isDemo() { return false; }

    @Override public List<FlightOffer> searchFlights(SearchCriteria criteria) {
        var url = config.url(Map.of(
            "origem", criteria.origin(),
            "destino", criteria.destination(),
            "ida", criteria.departureDate().toString(),
            "volta", criteria.returnDate().toString(),
            "adultos", String.valueOf(criteria.travelers())));
        var html = config.needsEntryPage()
            ? fetcher.fetchAfterVisiting(config.entryUrl(), url, config.waitFor(), Duration.ofSeconds(60))
            : fetcher.fetch(url, config.waitFor(), Duration.ofSeconds(60));
        return parse(html, criteria);
    }

    /** Publico de proposito: o teste roda o parser contra HTML gravado, sem rede. */
    public List<FlightOffer> parse(String html, SearchCriteria criteria) {
        var documento = Jsoup.parse(html);
        var itinerarios = documento.select(config.selector("card"));
        var precos = documento.select(config.selector("preco"));
        var ofertas = new ArrayList<FlightOffer>();
        var vistos = new HashSet<String>();

        // Itinerarios e precos vem em listas paralelas, na mesma ordem do documento.
        // Se as contagens divergirem, o layout mudou: melhor nao devolver nada do que
        // casar preco com o voo errado.
        if (itinerarios.size() != precos.size()) {
            LOG.warn("Decolar: {} itinerarios para {} precos — layout mudou ou a pagina veio incompleta",
                itinerarios.size(), precos.size());
            return List.of();
        }

        for (int i = 0; i < itinerarios.size(); i++) {
            toOffer(itinerarios.get(i), precos.get(i), i, criteria)
                .filter(oferta -> vistos.add(assinatura(oferta)))
                .ifPresent(ofertas::add);
        }
        return List.copyOf(ofertas);
    }

    private Optional<FlightOffer> toOffer(Element itinerario, Element preco, int index, SearchCriteria criteria) {
        var texto = itinerario.text();
        var horas = HORA.matcher(texto);
        if (!horas.find()) return Optional.empty();
        var partida = LocalTime.of(Integer.parseInt(horas.group(1)), Integer.parseInt(horas.group(2)));
        if (!horas.find()) return Optional.empty();
        var chegada = LocalTime.of(Integer.parseInt(horas.group(1)), Integer.parseInt(horas.group(2)));

        var valor = BrazilianText.firstMoney(preco.text());
        if (valor.isEmpty()) return Optional.empty();

        // O preco da Decolar ja e do grupo: a URL leva o numero de passageiros.
        // Multiplicar aqui, como e preciso no Google Voos, dobraria a conta.
        var paradas = paradas(texto);
        var bagagem = texto.contains("Não inclui bagagem para despachar");

        return Optional.of(new FlightOffer("decolar-" + index, companhia(itinerario), valor.get(),
            ZERO, ZERO, legs(criteria, paradas), bagagem ? 0.85 : 0.9,
            partida, chegada, duracao(texto)));
    }

    private static int paradas(String texto) {
        if (texto.contains("Direto") || texto.contains("Sem escalas")) return 0;
        var m = PARADAS.matcher(texto);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static Duration duracao(String texto) {
        var m = DURACAO.matcher(texto);
        if (!m.find()) return null;
        long horas = Long.parseLong(m.group(1));
        long minutos = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
        return Duration.ofHours(horas).plusMinutes(minutos);
    }

    /**
     * A Decolar mostra a companhia so como logo; o nome fica no alt da imagem.
     * Sem ler dali, todo card dizia "companhia nao identificada".
     */
    private static Airline companhia(Element itinerario) {
        var logo = itinerario.selectFirst("img[alt]");
        var nome = logo == null ? null : logo.attr("alt").trim();
        if (nome == null || nome.isBlank()) nome = itinerario.text();

        var minusculo = nome.toLowerCase(Locale.ROOT);
        for (var entrada : COMPANHIAS.entrySet()) {
            if (minusculo.contains(entrada.getKey().toLowerCase(Locale.ROOT))) {
                return new Airline(entrada.getValue(), entrada.getKey());
            }
        }
        return new Airline("--", "Companhia não identificada");
    }

    private static List<FlightLeg> legs(SearchCriteria criteria, int paradas) {
        var origem = new Location(criteria.origin(), null, null);
        var destino = new Location(criteria.destination(), null, null);
        if (paradas == 0) return List.of(new FlightLeg(origem, destino, null, false, false));
        var conexao = new Location("---", null, null);
        return List.of(new FlightLeg(origem, conexao, null, false, false),
                       new FlightLeg(conexao, destino, null, false, false));
    }

    private static String assinatura(FlightOffer oferta) {
        return oferta.departureTime() + "|" + oferta.arrivalTime() + "|" + oferta.rawFare().amount();
    }
}
