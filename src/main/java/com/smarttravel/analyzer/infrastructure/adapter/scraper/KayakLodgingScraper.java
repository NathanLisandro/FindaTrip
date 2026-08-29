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

/**
 * O Kayak compara o MESMO hotel em varios sites, e as diferencas sao grandes:
 * na captura de referencia, Rede Andrade Cecomtur saia por R$ 318 no Booking e R$ 280 no
 * Hotels.com. E o caminho para precos de sites que bloqueiam scraper direto.
 */
public class KayakLodgingScraper implements LodgingProviderPort {

    private static final String SITE = "kayak";
    private static final Path SCRAPERS = Path.of("scrapers");
    private static final Money ZERO = new Money(BigDecimal.ZERO, Money.BRL);

    /** "Fulano e uma propriedade de 3 estrelas localizada no bairro Downtown." */
    private static final Pattern NOME = Pattern.compile("^(.+?)\\s+é uma propriedade");
    private static final Pattern BAIRRO = Pattern.compile("no bairro\\s+([^.]+?)\\.");
    private static final Pattern NOTA = Pattern.compile("Tem nota\\s+(\\d+(?:,\\d+)?)\\s+com base em\\s+([\\d.]+)");

    private final PageFetcherPort fetcher;
    private final SiteConfig config;

    public KayakLodgingScraper(PageFetcherPort fetcher, SiteConfig config) {
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
        return parse(fetcher.fetch(url, config.waitFor(), Duration.ofSeconds(60)), criteria);
    }

    /** Publico de proposito: o teste roda o parser contra HTML gravado, sem rede. */
    public List<LodgingOffer> parse(String html, SearchCriteria criteria) {
        int noites = Math.max(1, (int) ChronoUnit.DAYS.between(criteria.departureDate(), criteria.returnDate()));
        var ofertas = new ArrayList<LodgingOffer>();
        var cartoes = Jsoup.parse(html).select(config.selector("card"));
        for (int i = 0; i < cartoes.size(); i++) {
            toOffer(cartoes.get(i), i, noites).ifPresent(ofertas::add);
        }
        return List.copyOf(ofertas);
    }

    private Optional<LodgingOffer> toOffer(Element cartao, int index, int noites) {
        var descricao = cartao.selectFirst(config.selector("descricao"));
        if (descricao == null) return Optional.empty();
        var texto = descricao.text();

        var nome = NOME.matcher(texto);
        if (!nome.find()) return Optional.empty();

        var cotacoes = cotacoes(cartao);
        if (cotacoes.isEmpty()) return Optional.empty();

        // O comparador existe para achar o menor preco: fica com ele, e diz de onde veio.
        var melhor = cotacoes.stream().min(Comparator.comparing(Cotacao::diaria)).orElseThrow();
        var total = new Money(melhor.diaria().amount().multiply(BigDecimal.valueOf(noites)), Money.BRL);

        var amenidades = EnumSet.noneOf(Amenity.class);
        var corpo = cartao.text();
        if (corpo.contains("Café da manhã")) amenidades.add(Amenity.BREAKFAST_INCLUDED);
        if (corpo.contains("Cancelamento grátis")) amenidades.add(Amenity.FREE_FLEXIBLE_CANCELLATION);

        // O Kayak declara "Diaria total - Incluindo todos impostos e taxas": nao ha taxa a somar.
        return Optional.of(new LodgingOffer("kayak-" + index, nome.group(1).trim(), bairro(texto),
            total, noites, ZERO, ZERO, ZERO, avaliacao(texto), Set.copyOf(amenidades), 0,
            fonte(melhor, cotacoes.size()), null, null));
    }

    /** Uma cotacao por site que o Kayak comparou. */
    private record Cotacao(String site, Money diaria) {}

    private List<Cotacao> cotacoes(Element cartao) {
        var encontradas = new ArrayList<Cotacao>();
        for (var logo : cartao.select(config.selector("provedor"))) {
            var site = logo.attr("alt").trim();
            if (site.isBlank()) continue;
            // O preco daquele site fica no bloco que contem o logo.
            var bloco = logo.parent();
            for (int subida = 0; subida < 4 && bloco != null; subida++) {
                var valor = BrazilianText.firstMoney(bloco.text());
                if (valor.isPresent()) { encontradas.add(new Cotacao(site, valor.get())); break; }
                bloco = bloco.parent();
            }
        }
        return encontradas;
    }

    private static String fonte(Cotacao melhor, int quantos) {
        return quantos > 1 ? melhor.site() + " — mais barato entre " + quantos + " sites" : melhor.site();
    }

    private static String bairro(String texto) {
        var m = BAIRRO.matcher(texto);
        if (!m.find()) return null;
        var valor = m.group(1).trim();
        return valor.isEmpty() ? null : valor;
    }

    private static HotelRating avaliacao(String texto) {
        var m = NOTA.matcher(texto);
        if (!m.find()) return new HotelRating(0, 0);
        return new HotelRating(Double.parseDouble(m.group(1).replace(',', '.')),
                               Integer.parseInt(m.group(2).replace(".", "")));
    }
}
