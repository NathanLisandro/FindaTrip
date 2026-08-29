package com.smarttravel.analyzer.domain.model.lodging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StayTypeTest {

    @Test void recognisesHotelsByName() {
        assertThat(StayType.fromText("Majestic Palace Hotel")).isEqualTo(StayType.HOTEL);
        assertThat(StayType.fromText("Interclass Florianópolis é uma propriedade de 4 estrelas")).isEqualTo(StayType.HOTEL);
    }

    @Test void aNameWithNoTypeWordNeedsTheRicherTextToBeClassified() {
        // "Ibis Florianopolis" nao diz o que e. A descricao do Kayak diz ("estrelas"),
        // entao o scraper classifica pelo texto mais rico que a fonte der, nao so pelo nome.
        assertThat(StayType.fromText("Ibis Florianopolis")).isEqualTo(StayType.OUTRO);
        assertThat(StayType.fromText("Ibis Florianopolis é uma propriedade de 3 estrelas"))
            .isEqualTo(StayType.HOTEL);
    }

    @Test void recognisesPousadas() {
        assertThat(StayType.fromText("Pousada Schmitz")).isEqualTo(StayType.POUSADA);
        assertThat(StayType.fromText("Belle Arti Pousada")).isEqualTo(StayType.POUSADA);
    }

    @Test void recognisesApartmentsAndStudios() {
        assertThat(StayType.fromText("Apartamento ⋅ Florianópolis")).isEqualTo(StayType.APARTAMENTO);
        assertThat(StayType.fromText("Studio em Cond. c/ Piscina")).isEqualTo(StayType.APARTAMENTO);
        assertThat(StayType.fromText("Estúdio confortável ao lado da Beira Mar")).isEqualTo(StayType.APARTAMENTO);
        assertThat(StayType.fromText("Apto em Cond. c/ Piscina 230m da Praia")).isEqualTo(StayType.APARTAMENTO);
    }

    @Test void recognisesWholeHouses() {
        assertThat(StayType.fromText("Casa de Temporada Jardim")).isEqualTo(StayType.CASA);
        assertThat(StayType.fromText("Bangalô Acerola - Estacionamento")).isEqualTo(StayType.CASA);
        assertThat(StayType.fromText("Chalé na Lagoa")).isEqualTo(StayType.CASA);
    }

    @Test void recognisesHostels() {
        assertThat(StayType.fromText("Hostel do Porto")).isEqualTo(StayType.HOSTEL);
        assertThat(StayType.fromText("Floripa Hostel Lagoa")).isEqualTo(StayType.HOSTEL);
    }

    @Test void doesNotGuessWhenNothingInTheNameSaysSo() {
        // Chutar tipo faz o filtro esconder opcao boa. Melhor admitir que nao sabe.
        assertThat(StayType.fromText("Recanto das Flores")).isEqualTo(StayType.OUTRO);
        assertThat(StayType.fromText(null)).isEqualTo(StayType.OUTRO);
    }

    @Test void aPousadaNamedHotelIsStillReadAsPousadaBecauseItComesFirst() {
        assertThat(StayType.fromText("Pousada e Hotel Mar Azul")).isEqualTo(StayType.POUSADA);
    }
}
