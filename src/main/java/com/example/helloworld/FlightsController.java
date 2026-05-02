package com.example.helloworld;

import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
public class FlightsController {

    private final RestTemplate rest;

    @Value("${opensky.username:}")
    private String openskyUsername;

    @Value("${opensky.password:}")
    private String openskyPassword;

    public FlightsController(org.springframework.boot.web.client.RestTemplateBuilder builder) {
        this.rest = builder
                .setConnectTimeout(java.time.Duration.ofSeconds(10))
                .setReadTimeout(java.time.Duration.ofSeconds(20))
                .build();
    }

    /**
     * Proxy for the OpenSky Network API — avoids browser CORS restrictions.
     * Authenticated access uses OPENSKY_USERNAME / OPENSKY_PASSWORD env vars.
     */
    @GetMapping("/api/flights")
    public ResponseEntity<String> getFlights(
            @RequestParam(defaultValue = "-35.5") String lamin,
            @RequestParam(defaultValue = "-32.5") String lamax,
            @RequestParam(defaultValue = "149.5") String lomin,
            @RequestParam(defaultValue = "153.5") String lomax) {

        String url = String.format(
                "https://opensky-network.org/api/states/all?lamin=%s&lamax=%s&lomin=%s&lomax=%s",
                lamin, lamax, lomin, lomax);

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json");

        if (openskyUsername != null && !openskyUsername.isBlank()) {
            String credentials = openskyUsername + ":" + openskyPassword;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
            headers.set("Authorization", "Basic " + encoded);
        }

        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(resp.getBody());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "unknown error";
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\":\"" + msg + "\"}");
        }
    }
}
