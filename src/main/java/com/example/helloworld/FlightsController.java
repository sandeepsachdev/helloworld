package com.example.helloworld;

import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
public class FlightsController {

    private static final Logger log = LoggerFactory.getLogger(FlightsController.class);

    private final RestTemplate rest;

    @Value("${opensky.username:}")
    private String openskyUsername;

    @Value("${opensky.password:}")
    private String openskyPassword;

    public FlightsController(org.springframework.boot.web.client.RestTemplateBuilder builder) {
        this.rest = builder
                .setConnectTimeout(java.time.Duration.ofSeconds(30))
                .setReadTimeout(java.time.Duration.ofSeconds(60))
                .build();
    }

    /**
     * Proxy for the adsb.lol API — no auth required, radius-based query.
     * Returns altitude in feet and speed in knots (unlike OpenSky which uses metres/m/s).
     */
    @GetMapping("/api/flights-adsb")
    public ResponseEntity<String> getAdsbFlights(
            @RequestParam(defaultValue = "51.4775") String lat,
            @RequestParam(defaultValue = "-0.4614") String lon,
            @RequestParam(defaultValue = "150")     String radius) {

        String url = String.format(
                "https://api.adsb.lol/v2/lat/%s/lon/%s/dist/%s",
                lat, lon, radius);

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json");

        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            log.debug("adsb.lol request: lat={} lon={} radius={}nm", lat, lon, radius);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(resp.getBody());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "unknown error";
            log.error("adsb.lol request failed: {} {}", e.getClass().getSimpleName(), msg, e);
            String body = String.format(
                    "{\"error\":\"%s\",\"type\":\"%s\",\"url\":\"%s\"}",
                    msg, e.getClass().getSimpleName(), url);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }

    /**
     * Proxy for the adsbdb.com callsign route API — returns airline, origin and destination airport.
     */
    @GetMapping("/api/route")
    public ResponseEntity<String> getRoute(@RequestParam String callsign) {
        // Only allow alphanumeric + hyphens to prevent path traversal
        if (!callsign.matches("[A-Za-z0-9\\-]{1,12}")) {
            return ResponseEntity.badRequest().body("{\"error\":\"invalid callsign\"}");
        }
        String url = "https://api.adsbdb.com/v0/callsign/" + callsign.toUpperCase();

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json");

        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            log.debug("adsbdb route lookup: callsign={}", callsign);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(resp.getBody());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "unknown error";
            log.error("adsbdb route lookup failed: {} {}", e.getClass().getSimpleName(), msg, e);
            String body = String.format(
                    "{\"error\":\"%s\",\"type\":\"%s\",\"url\":\"%s\"}",
                    msg, e.getClass().getSimpleName(), url);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
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
            log.debug("OpenSky request: authenticated as '{}'", openskyUsername);
        } else {
            log.debug("OpenSky request: anonymous (no OPENSKY_USERNAME set)");
        }

        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(resp.getBody());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "unknown error";
            log.error("OpenSky request failed: {} {}", e.getClass().getSimpleName(), msg, e);
            String body = String.format(
                    "{\"error\":\"%s\",\"type\":\"%s\",\"url\":\"%s\"}",
                    msg, e.getClass().getSimpleName(), url);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}
