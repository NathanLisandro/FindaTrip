package com.smarttravel.analyzer.presentation.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class SearchControllerTest {

    @Autowired WebApplicationContext context;

    private MockMvc mockMvc() { return MockMvcBuilders.webAppContextSetup(context).build(); }

    private static final String BODY = """
        {"origin":"CWB","destination":"REC","departureDate":"2030-11-10","returnDate":"2030-11-13",
         "travelers":2,"checkedBagRequested":true,"carRequired":false,"flexibleDates":false}""";

    /**
     * A busca e assincrona de proposito. Ler antes de ela terminar devolve listas vazias e
     * faz o teste falhar de vez em quando — trocar o executor por sincrono esconderia
     * justamente o comportamento que se quer testar, entao o teste espera.
     */
    private String startSearchAndWait() throws Exception {
        var id = startSearch();
        for (int tentativa = 0; tentativa < 100; tentativa++) {
            var corpo = mockMvc().perform(get("/api/search/{id}", id)).andReturn().getResponse().getContentAsString();
            if (!corpo.contains("\"status\":\"BUSCANDO\"")) return id;
            Thread.sleep(100);
        }
        throw new AssertionError("A busca nao terminou em 10 segundos");
    }

    private String startSearch() throws Exception {
        var response = mockMvc().perform(post("/api/search").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.searchId").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"searchId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test void startingASearchReturnsAnIdImmediately() throws Exception {
        org.assertj.core.api.Assertions.assertThat(startSearch()).isNotBlank();
    }

    @Test void theResultCarriesThreeRecommendationsAndTheDemoFlag() throws Exception {
        var id = startSearchAndWait();
        mockMvc().perform(get("/api/search/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.demo").value(true))
            .andExpect(jsonPath("$.packages.length()").value(org.hamcrest.Matchers.greaterThan(3)))
            .andExpect(jsonPath("$.packages[0].recommendationType").value("BEST_VALUE_OVERALL"))
            .andExpect(jsonPath("$.totalMatching").value(org.hamcrest.Matchers.greaterThan(0)))
            .andExpect(jsonPath("$.packages[0].realCost").isNotEmpty())
            .andExpect(jsonPath("$.packages[0].advertisedPrice").isNotEmpty())
            .andExpect(jsonPath("$.packages[0].why").isNotEmpty())
            .andExpect(jsonPath("$.packages[0].lodgingName").isNotEmpty())
            .andExpect(jsonPath("$.packages[0].neighborhood").isNotEmpty())
            .andExpect(jsonPath("$.packages[0].links.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test void theResultListsTheNeighborhoodsFoundWithCountAndCheapestPrice() throws Exception {
        var id = startSearchAndWait();
        mockMvc().perform(get("/api/search/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.neighborhoods.length()").value(org.hamcrest.Matchers.greaterThan(1)))
            .andExpect(jsonPath("$.neighborhoods[0].neighborhood").isNotEmpty())
            .andExpect(jsonPath("$.neighborhoods[0].packages").isNumber())
            .andExpect(jsonPath("$.neighborhoods[0].cheapest").isNotEmpty());
    }

    @Test void filteringByNeighborhoodKeepsTheNeighborhoodOptionsIntact() throws Exception {
        var id = startSearchAndWait();
        mockMvc().perform(get("/api/search/{id}", id).param("neighborhood", "bairro-que-nao-existe"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.packages.length()").value(0))
            .andExpect(jsonPath("$.neighborhoods.length()").value(org.hamcrest.Matchers.greaterThan(1)));
    }

    @Test void everySourceIsReportedWithItsHealth() throws Exception {
        var id = startSearchAndWait();
        mockMvc().perform(get("/api/search/{id}", id))
            .andExpect(jsonPath("$.sources.length()").value(2))
            .andExpect(jsonPath("$.sources[0].health").value("OK"));
    }

    @Test void anImpossibleFilterReturnsAnEmptyPackageListNotAnError() throws Exception {
        var id = startSearchAndWait();
        mockMvc().perform(get("/api/search/{id}", id).param("maxPrice", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.packages.length()").value(0));
    }

    @Test void aFilterPicksTheBestThreeAmongMatchingCandidatesInsteadOfTrimmingTheFinalThree() throws Exception {
        var id = startSearchAndWait();
        mockMvc().perform(get("/api/search/{id}", id).param("directFlightOnly", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.packages.length()").value(org.hamcrest.Matchers.greaterThan(1)))
            .andExpect(jsonPath("$.packages[*].stops").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(0))));
    }

    @Test void theNeighborhoodOptionsComeFromEveryCandidateNotJustTheThreeRecommendations() throws Exception {
        var id = startSearchAndWait();
        mockMvc().perform(get("/api/search/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.neighborhoods.length()").value(org.hamcrest.Matchers.greaterThan(2)));
    }

    @Test void noRecommendationIsRepeatedUnderTwoDifferentLabels() throws Exception {
        var id = startSearchAndWait();
        var body = mockMvc().perform(get("/api/search/{id}", id)).andReturn().getResponse().getContentAsString();
        var ids = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"").matcher(body).results()
            .map(match -> match.group(1)).toList();
        org.assertj.core.api.Assertions.assertThat(ids).doesNotHaveDuplicates();
    }

    @Test void theResultOffersTheStayTypesFoundSoTheUserCanNarrowByKindOfPlace() throws Exception {
        var id = startSearchAndWait();
        mockMvc().perform(get("/api/search/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stayTypes.length()").value(org.hamcrest.Matchers.greaterThan(0)))
            .andExpect(jsonPath("$.stayTypes[0].label").isNotEmpty())
            .andExpect(jsonPath("$.stayTypes[0].packages").isNumber());
    }

    @Test void filteringByAStayTypeNarrowsThePackagesButKeepsTheOptions() throws Exception {
        var id = startSearchAndWait();
        mockMvc().perform(get("/api/search/{id}", id).param("stayTypes", "HOSTEL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stayTypes.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test void anUnknownSearchIdReturnsNotFound() throws Exception {
        mockMvc().perform(get("/api/search/{id}", "does-not-exist")).andExpect(status().isNotFound());
    }

    @Test void aRequestWithoutOriginIsRejected() throws Exception {
        mockMvc().perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"origin\":\"CWB\"", "\"origin\":\"\"")))
            .andExpect(status().isBadRequest());
    }
}
