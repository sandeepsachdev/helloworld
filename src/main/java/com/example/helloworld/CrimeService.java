package com.example.helloworld;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CrimeService {

    private static final String CKAN_BASE = "https://data.nsw.gov.au/data/api/action/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Known stable resource IDs for NSW Recorded Crime Statistics (suburb-level).
    // These are tried in order; the first one with active datastore is used.
    private static final List<String> CANDIDATE_RESOURCE_IDS = List.of(
            "b035a4a3-26a4-4f59-9c56-ce5fb71b3d1b",  // NSW Recorded Crime Statistics (suburb)
            "6d05e0e2-e79a-4843-8a73-bc74b6ea3f0e"   // fallback
    );

    private final RestTemplate restTemplate;

    // Cached resource ID so we don't re-discover on every request
    private volatile String cachedResourceId = null;

    public CrimeService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public Map<String, Object> getCrimeData(String suburb, int startYear, int endYear, String crimeType) {
        try {
            String resourceId = resolveResourceId();
            if (resourceId == null) {
                return errorResponse("Could not locate crime statistics dataset on NSW Open Data portal.");
            }

            // CKAN datastore_search: query by suburb text, fetch up to 32000 records
            String url = UriComponentsBuilder.fromHttpUrl(CKAN_BASE + "datastore_search")
                    .queryParam("resource_id", resourceId)
                    .queryParam("q", suburb)
                    .queryParam("limit", 32000)
                    .build(false)
                    .toUriString();

            String raw = restTemplate.getForObject(url, String.class);
            JsonNode root = MAPPER.readTree(raw);

            if (!root.path("success").asBoolean()) {
                return errorResponse("NSW Open Data API returned an error: " + root.path("error").toString());
            }

            JsonNode resultNode = root.path("result");
            JsonNode recordsNode = resultNode.path("records");

            // Determine available columns (skip internal CKAN _id)
            List<String> columns = new ArrayList<>();
            for (JsonNode field : resultNode.path("fields")) {
                String id = field.path("id").asText();
                if (!id.startsWith("_")) columns.add(id);
            }

            // Identify year column (various names used across BOCSAR datasets)
            String yearCol = columns.stream()
                    .filter(c -> c.toLowerCase().contains("year") || c.equalsIgnoreCase("Year"))
                    .findFirst().orElse(null);

            String categoryCol = columns.stream()
                    .filter(c -> c.toLowerCase().contains("offence") || c.toLowerCase().contains("category") || c.toLowerCase().contains("bcsrcat"))
                    .findFirst().orElse(null);

            String suburbCol = columns.stream()
                    .filter(c -> c.toLowerCase().contains("suburb") || c.toLowerCase().contains("locality"))
                    .findFirst().orElse(null);

            List<Map<String, Object>> filtered = new ArrayList<>();
            Set<String> categories = new LinkedHashSet<>();
            TreeSet<Integer> years = new TreeSet<>();

            for (JsonNode rec : recordsNode) {
                // Filter by year range
                if (yearCol != null) {
                    String yearStr = rec.path(yearCol).asText("");
                    // year may be stored as "2022" or "2022 Q1" – extract first 4 digits
                    String digits = yearStr.replaceAll("[^0-9]", "");
                    if (digits.length() >= 4) {
                        int y = Integer.parseInt(digits.substring(0, 4));
                        if (y < startYear || y > endYear) continue;
                        years.add(y);
                    }
                }

                // Filter by suburb (case-insensitive contains)
                if (suburbCol != null) {
                    String s = rec.path(suburbCol).asText("").toLowerCase();
                    if (!s.contains(suburb.toLowerCase())) continue;
                }

                // Filter by crime type if specified
                if (crimeType != null && !crimeType.isBlank() && categoryCol != null) {
                    String cat = rec.path(categoryCol).asText("").toLowerCase();
                    if (!cat.contains(crimeType.toLowerCase())) continue;
                }

                Map<String, Object> row = new LinkedHashMap<>();
                for (String col : columns) {
                    JsonNode val = rec.path(col);
                    row.put(col, val.isNull() ? "" : val.asText());
                }
                if (categoryCol != null) categories.add(rec.path(categoryCol).asText(""));
                filtered.add(row);
            }

            String yearRange = years.isEmpty() ? startYear + "–" + endYear
                    : years.first() + "–" + years.last();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("records", filtered);
            result.put("columns", columns);
            result.put("total", filtered.size());
            result.put("suburb", suburb);
            result.put("yearRange", yearRange);
            result.put("uniqueCategories", categories.size());
            result.put("resourceId", resourceId);
            return result;

        } catch (Exception e) {
            return errorResponse("Failed to fetch crime data: " + e.getMessage());
        }
    }

    private String resolveResourceId() throws Exception {
        if (cachedResourceId != null) return cachedResourceId;

        // Try known IDs first
        for (String id : CANDIDATE_RESOURCE_IDS) {
            if (datastoreIsActive(id)) {
                cachedResourceId = id;
                return id;
            }
        }

        // Fall back: search the CKAN catalogue for a crime statistics package
        String searchUrl = CKAN_BASE + "package_search?q=recorded+crime+statistics+suburb&rows=10";
        String raw = restTemplate.getForObject(searchUrl, String.class);
        JsonNode root = MAPPER.readTree(raw);

        for (JsonNode pkg : root.path("result").path("results")) {
            for (JsonNode res : pkg.path("resources")) {
                if (res.path("datastore_active").asBoolean()) {
                    String id = res.path("id").asText();
                    cachedResourceId = id;
                    return id;
                }
            }
        }

        return null;
    }

    private boolean datastoreIsActive(String resourceId) {
        try {
            String url = CKAN_BASE + "datastore_search?resource_id=" + resourceId + "&limit=1";
            String raw = restTemplate.getForObject(url, String.class);
            JsonNode root = MAPPER.readTree(raw);
            return root.path("success").asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("error", message);
        r.put("records", List.of());
        r.put("total", 0);
        return r;
    }
}
