package com.example.helloworld;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class CrimeController {

    private final CrimeService crimeService;

    public CrimeController(CrimeService crimeService) {
        this.crimeService = crimeService;
    }

    /** JSON API endpoint queried by the frontend. */
    @GetMapping("/api/crime")
    public Map<String, Object> getCrimeData(
            @RequestParam(defaultValue = "Cherrybrook") String suburb,
            @RequestParam(defaultValue = "2015") int startYear,
            @RequestParam(defaultValue = "2025") int endYear,
            @RequestParam(required = false) String crimeType) {
        return crimeService.getCrimeData(suburb, startYear, endYear, crimeType);
    }
}
