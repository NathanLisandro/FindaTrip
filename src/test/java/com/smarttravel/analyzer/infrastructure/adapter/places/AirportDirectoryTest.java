package com.smarttravel.analyzer.infrastructure.adapter.places;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AirportDirectoryTest {

    private final AirportDirectory diretorio = new AirportDirectory();

    @Test void findsMaringaByCityNameSoNobodyNeedsToKnowTheCode() {
        assertThat(diretorio.search("maring", 8)).first()
            .satisfies(a -> {
                assertThat(a.code()).isEqualTo("MGF");
                assertThat(a.city()).isEqualTo("Maringá");
            });
    }

    @Test void ignoresAccentsAndCase() {
        assertThat(diretorio.search("FLORIANOPOLIS", 8)).extracting(Airport::code).contains("FLN");
        assertThat(diretorio.search("florianópolis", 8)).extracting(Airport::code).contains("FLN");
    }

    @Test void stillAcceptsTheCodeForWhoAlreadyKnowsIt() {
        assertThat(diretorio.search("MGF", 8)).first()
            .satisfies(a -> assertThat(a.code()).isEqualTo("MGF"));
    }

    @Test void putsTheExactCodeMatchFirst() {
        // "GRU" nao pode vir atras de um aeroporto qualquer que contenha "gru" no nome.
        assertThat(diretorio.search("gru", 8)).first()
            .satisfies(a -> assertThat(a.code()).isEqualTo("GRU"));
    }

    @Test void respectsTheRequestedLimit() {
        assertThat(diretorio.search("a", 5)).hasSizeLessThanOrEqualTo(5);
    }

    @Test void anEmptyQueryReturnsNothingInsteadOfEverything() {
        assertThat(diretorio.search("", 8)).isEmpty();
        assertThat(diretorio.search(null, 8)).isEmpty();
        assertThat(diretorio.search(" ", 8)).isEmpty();
    }

    @Test void loadsTheWholeBrazilianDirectory() {
        assertThat(diretorio.size()).isGreaterThan(200);
    }
}
