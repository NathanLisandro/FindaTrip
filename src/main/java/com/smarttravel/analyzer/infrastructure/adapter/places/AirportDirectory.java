package com.smarttravel.analyzer.infrastructure.adapter.places;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Busca de aeroporto por nome de cidade.
 * "MGF" nao e algo que se saiba de cabeca; o usuario digita "maringa" e escolhe.
 * A lista e estatica e vai junto no jar: uma busca de aeroporto nao pode depender de rede.
 */
@Component
public class AirportDirectory {

    /** Como as pessoas realmente digitam. Sem isto, "floripa" nao acha nada. */
    private static final java.util.Map<String, String> APELIDOS = java.util.Map.of(
        "floripa", "florianopolis",
        "sampa", "guarulhos",
        "sao paulo", "guarulhos",
        "bh", "belo horizonte",
        "rio", "rio de janeiro",
        "brasilia", "brasilia");

    private final List<Airport> aeroportos;

    public AirportDirectory() {
        try (var entrada = new ClassPathResource("data/aeroportos-br.json").getInputStream()) {
            this.aeroportos = new ObjectMapper().readValue(entrada, new TypeReference<List<Airport>>() {});
        } catch (IOException falha) {
            throw new UncheckedIOException("Nao foi possivel ler a lista de aeroportos", falha);
        }
    }

    public int size() { return aeroportos.size(); }

    public List<Airport> search(String termo, int limite) {
        if (termo == null || termo.isBlank()) return List.of();
        var alvo = APELIDOS.getOrDefault(semAcento(termo), semAcento(termo));
        return aeroportos.stream()
            .filter(a -> a.search().contains(alvo))
            // Codigo exato primeiro: quem digita "GRU" quer Guarulhos, nao um nome que contenha "gru".
            .sorted(Comparator.comparingInt((Airport a) -> a.code().equalsIgnoreCase(termo.trim()) ? 0 : 1)
                .thenComparingInt(a -> semAcento(a.city()).startsWith(alvo) ? 0 : 1)
                .thenComparing(Airport::city))
            .limit(Math.max(0, limite))
            .toList();
    }

    private static String semAcento(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
    }
}
