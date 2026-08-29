package com.smarttravel.analyzer.presentation.rest;

import com.smarttravel.analyzer.infrastructure.adapter.places.Airport;
import com.smarttravel.analyzer.infrastructure.adapter.places.AirportDirectory;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Autocomplete de cidade: o usuario digita "maringa" e o app resolve o codigo. */
@RestController
@RequestMapping("/api/places")
public class PlacesController {

    private final AirportDirectory diretorio;

    public PlacesController(AirportDirectory diretorio) { this.diretorio = diretorio; }

    @GetMapping
    public List<Airport> buscar(@RequestParam String q,
                                @RequestParam(defaultValue = "8") int limite) {
        return diretorio.search(q, Math.min(limite, 20));
    }
}
