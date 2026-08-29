package com.smarttravel.analyzer.domain.model.lodging;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Que tipo de lugar e a hospedagem. Nenhuma das fontes publica isso num campo proprio,
 * entao vem do nome do anuncio — que e como o proprio usuario reconhece o lugar.
 */
public enum StayType {
    HOTEL("Hotel"),
    POUSADA("Pousada"),
    APARTAMENTO("Apartamento ou estúdio"),
    CASA("Casa inteira"),
    HOSTEL("Hostel"),
    OUTRO("Outro");

    private final String rotulo;

    StayType(String rotulo) { this.rotulo = rotulo; }

    public String rotulo() { return rotulo; }

    /**
     * A ORDEM importa: "Pousada e Hotel Mar Azul" e pousada, e quem aparece primeiro
     * na lista vence. Sem palavra reconhecida devolve OUTRO — chutar faria o filtro
     * esconder opcao boa.
     */
    private static final Map<StayType, List<String>> PISTAS = Map.of(
        POUSADA, List.of("pousada", "inn"),
        HOSTEL, List.of("hostel", "albergue"),
        CASA, List.of("casa", "chale", "chalet", "bangalo", "sitio", "villa", "cabana"),
        APARTAMENTO, List.of("apartamento", "apto", "studio", "estudio", "kitnet", "flat", "loft", "apart"),
        HOTEL, List.of("hotel", "resort", "estrelas"));

    private static final List<StayType> ORDEM = List.of(POUSADA, HOSTEL, CASA, APARTAMENTO, HOTEL);

    public static StayType fromText(String texto) {
        if (texto == null || texto.isBlank()) return OUTRO;
        var limpo = semAcento(texto);
        for (var tipo : ORDEM) {
            for (var pista : PISTAS.get(tipo)) {
                if (contemPalavra(limpo, pista)) return tipo;
            }
        }
        return OUTRO;
    }

    /**
     * Palavra inteira, nao pedaco: "Village" contem "villa" e fazia um estudio
     * ser classificado como casa inteira, escondendo-o do filtro certo.
     */
    private static boolean contemPalavra(String texto, String palavra) {
        return java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(palavra) + "\\b")
            .matcher(texto).find();
    }

    private static String semAcento(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
