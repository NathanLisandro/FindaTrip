package com.smarttravel.analyzer.presentation.rest;

import com.smarttravel.analyzer.infrastructure.adapter.image.ImageProxyPolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Serve as fotos dos anuncios pelo backend.
 * Hotlink direto do CDN de Booking e Airbnb e bloqueado por referer; buscar aqui resolve
 * isso e evita bater no CDN deles a cada render do navegador.
 */
@RestController
@RequestMapping("/api/img")
public class ImageController {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @GetMapping
    public ResponseEntity<byte[]> proxy(@RequestParam String ref) {
        if (!ImageProxyPolicy.allows(ref)) return ResponseEntity.badRequest().build();
        try {
            var request = HttpRequest.newBuilder(URI.create(ref))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0")
                .GET().build();
            var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            var tipo = response.headers().firstValue("content-type").orElse("image/jpeg");
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipo))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
                .body(response.body());
        } catch (java.io.IOException falha) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (InterruptedException interrompida) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
