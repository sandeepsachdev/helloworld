package com.example.helloworld;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SpecialsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

    private final RestTemplate rest;
    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

    public SpecialsService(org.springframework.boot.web.client.RestTemplateBuilder builder) {
        this.rest = builder
                .setConnectTimeout(java.time.Duration.ofSeconds(10))
                .setReadTimeout(java.time.Duration.ofSeconds(20))
                .build();
    }

    // ── Cache wrapper ────────────────────────────────────────────────────────

    private record CachedResult(Map<String, Object> data, long timestamp) {}

    private Map<String, Object> cached(String key, java.util.function.Supplier<Map<String, Object>> fn) {
        CachedResult c = cache.get(key);
        if (c != null && System.currentTimeMillis() - c.timestamp() < CACHE_TTL_MS) {
            return c.data();
        }
        Map<String, Object> result = fn.get();
        cache.put(key, new CachedResult(result, System.currentTimeMillis()));
        return result;
    }

    // ── Common browser headers ────────────────────────────────────────────────

    private HttpHeaders browserHeaders(String referer) {
        HttpHeaders h = new HttpHeaders();
        h.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        h.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        h.set("Accept-Language", "en-AU,en;q=0.9");
        h.set("Referer", referer);
        return h;
    }

    private HttpHeaders jsonHeaders(String referer) {
        HttpHeaders h = browserHeaders(referer);
        h.set("Accept", "application/json, text/plain, */*");
        h.set("Content-Type", "application/json");
        h.set("Origin", "https://www.woolworths.com.au");
        return h;
    }

    // ── Result builders ───────────────────────────────────────────────────────

    private Map<String, Object> ok(String store, List<Map<String, Object>> products, int page, int total) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("store", store);
        r.put("products", products);
        r.put("page", page);
        r.put("total", total);
        r.put("updatedAt", Instant.now().toString());
        r.put("error", null);
        return r;
    }

    private Map<String, Object> err(String store, String message, String link) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("store", store);
        r.put("products", Collections.emptyList());
        r.put("error", message);
        r.put("link", link);
        r.put("updatedAt", Instant.now().toString());
        return r;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WOOLWORTHS — POST to their internal specials browse API
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> getWoolworths(int page) {
        String cacheKey = "woolworths_" + page;
        return cached(cacheKey, () -> fetchWoolworths(page));
    }

    private Map<String, Object> fetchWoolworths(int page) {
        try {
            String url = "https://www.woolworths.com.au/apis/ui/Specials/browse/All";
            String body = String.format("""
                {
                  "categoryId": "",
                  "pageNumber": %d,
                  "pageSize": 36,
                  "sortType": "TrackPrice",
                  "url": "/shop/specials/halfprice",
                  "location": "/shop/specials/halfprice",
                  "formatObject": "{\\"name\\":\\"Specials\\"}",
                  "isSpecial": false,
                  "isBundle": false,
                  "isMobile": false,
                  "filters": []
                }""", page);

            HttpHeaders headers = jsonHeaders("https://www.woolworths.com.au/shop/specials/halfprice");
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST, entity, String.class);

            JsonNode root = MAPPER.readTree(resp.getBody());
            JsonNode productsNode = root.path("Products");
            int total = root.path("TotalRecordCount").asInt(0);

            List<Map<String, Object>> products = new ArrayList<>();
            if (productsNode.isArray()) {
                for (JsonNode p : productsNode) {
                    Map<String, Object> product = new LinkedHashMap<>();
                    product.put("name", p.path("Name").asText(""));
                    product.put("price", p.path("Price").asDouble(0));
                    product.put("wasPrice", p.path("WasPrice").asDouble(0));
                    product.put("packageSize", p.path("PackageSize").asText(""));
                    product.put("imageUrl", p.path("SmallImageFile").asText(""));
                    product.put("category", p.path("AdditionalAttributes").path("category").asText(""));
                    product.put("promo", p.path("PromotionDescription").asText(""));
                    product.put("stockcode", p.path("Stockcode").asLong(0));
                    products.add(product);
                }
            }

            if (products.isEmpty()) {
                return err("woolworths", "No products returned — Woolworths may require a login session.",
                        "https://www.woolworths.com.au/shop/specials/halfprice");
            }

            return ok("woolworths", products, page, total);

        } catch (Exception e) {
            return err("woolworths",
                    "Could not reach Woolworths API: " + simplify(e.getMessage()),
                    "https://www.woolworths.com.au/shop/specials/halfprice");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COLES — fetch HTML page, extract __NEXT_DATA__ JSON
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> getColes(int page) {
        return cached("coles_" + page, () -> fetchColes(page));
    }

    private Map<String, Object> fetchColes(int page) {
        try {
            HttpHeaders headers = browserHeaders("https://www.google.com.au/");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = rest.exchange(
                    "https://www.coles.com.au/on-special", HttpMethod.GET, entity, String.class);

            String html = resp.getBody();
            if (html == null) throw new Exception("Empty response");

            // Try to extract __NEXT_DATA__ embedded JSON
            int start = html.indexOf("id=\"__NEXT_DATA__\"");
            if (start != -1) {
                start = html.indexOf(">", start) + 1;
                int end = html.indexOf("</script>", start);
                if (end > start) {
                    String json = html.substring(start, end).trim();
                    return parseColesNextData(json, page);
                }
            }

            // Fallback: use jsoup to extract what we can from HTML
            return parseColesHtml(html, page);

        } catch (Exception e) {
            return err("coles",
                    "Could not reach Coles website: " + simplify(e.getMessage()),
                    "https://www.coles.com.au/on-special");
        }
    }

    private Map<String, Object> parseColesNextData(String json, int page) {
        try {
            JsonNode root = MAPPER.readTree(json);
            // Navigate typical Next.js page props structure
            JsonNode pageProps = root.path("props").path("pageProps");

            // Try various known paths for specials data
            JsonNode searchResults = pageProps.path("searchResults");
            JsonNode results = searchResults.path("results");
            if (!results.isArray()) results = pageProps.path("initialProducts");
            if (!results.isArray()) {
                // Try catalogues/specials array
                JsonNode catalog = pageProps.path("catalog");
                if (!catalog.isMissingNode()) results = catalog.path("results");
            }

            if (!results.isArray() || results.isEmpty()) {
                return err("coles", "Coles page structure has changed — live data unavailable.",
                        "https://www.coles.com.au/on-special");
            }

            List<Map<String, Object>> products = new ArrayList<>();
            for (JsonNode p : results) {
                if (products.size() >= 36) break;
                Map<String, Object> product = new LinkedHashMap<>();
                product.put("name", text(p, "name", "brand", "_source.name"));
                product.put("price", price(p, "pricing.now", "price", "_source.pricing.now"));
                product.put("wasPrice", price(p, "pricing.was", "wasPrice", "_source.pricing.was"));
                product.put("packageSize", text(p, "size", "packageSize"));
                product.put("imageUrl", text(p, "imageUris[0]", "images[0].uri", "image"));
                product.put("category", text(p, "onPromotion", "category"));
                product.put("promo", text(p, "pricing.promotionType", "promotionType"));
                products.add(product);
            }

            return ok("coles", products, page, products.size());
        } catch (Exception e) {
            return err("coles", "Failed to parse Coles data: " + simplify(e.getMessage()),
                    "https://www.coles.com.au/on-special");
        }
    }

    private Map<String, Object> parseColesHtml(String html, int page) {
        try {
            Document doc = Jsoup.parse(html);
            Elements items = doc.select("[data-testid='product-tile'], .product-tile, .coles-targeting-ProductTile");
            if (items.isEmpty()) {
                return err("coles", "Coles blocks automated access. Visit their site directly.",
                        "https://www.coles.com.au/on-special");
            }
            List<Map<String, Object>> products = new ArrayList<>();
            for (Element item : items) {
                if (products.size() >= 36) break;
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", item.select("[data-testid='product-title'], .product__title, h2").text());
                p.put("price", 0.0);
                p.put("wasPrice", 0.0);
                p.put("packageSize", "");
                p.put("imageUrl", item.select("img").attr("src"));
                p.put("category", "");
                p.put("promo", "Special");
                products.add(p);
            }
            return ok("coles", products, page, products.size());
        } catch (Exception e) {
            return err("coles", "Coles HTML parse failed.", "https://www.coles.com.au/on-special");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ALDI — scrape weekly specials HTML page
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> getAldi() {
        return cached("aldi", this::fetchAldi);
    }

    private Map<String, Object> fetchAldi() {
        try {
            HttpHeaders headers = browserHeaders("https://www.aldi.com.au/");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = rest.exchange(
                    "https://www.aldi.com.au/en/specials/this-weeks-specials/", HttpMethod.GET, entity, String.class);

            String html = resp.getBody();
            if (html == null) throw new Exception("Empty response");

            Document doc = Jsoup.parse(html);
            List<Map<String, Object>> products = new ArrayList<>();

            // Aldi uses mod-article-tile components for specials
            Elements tiles = doc.select(".mod-article-tile, .mod--isSpecials .mod-article-tile, article.mod-article-tile");
            if (tiles.isEmpty()) {
                // Try alternate selectors
                tiles = doc.select("[class*='article-tile'], [class*='specials-item']");
            }

            for (Element tile : tiles) {
                if (products.size() >= 48) break;
                String name = tile.select(".mod-article-tile__headline, h2, h3, [class*='title'], [class*='name']").text().trim();
                if (name.isBlank()) continue;

                String priceText = tile.select(".price-tag, [class*='price'], .price").text().trim();
                double price = parsePrice(priceText);
                String image = tile.select("img").attr("src");
                if (image.isBlank()) image = tile.select("img").attr("data-src");
                String category = tile.select("[class*='category'], [class*='tag']").text().trim();
                String desc = tile.select("[class*='description'], [class*='copy'], p").text().trim();

                Map<String, Object> product = new LinkedHashMap<>();
                product.put("name", name);
                product.put("price", price);
                product.put("wasPrice", 0.0);
                product.put("packageSize", desc.length() > 80 ? desc.substring(0, 80) : desc);
                product.put("imageUrl", image);
                product.put("category", category);
                product.put("promo", "Weekly Special");
                products.add(product);
            }

            if (products.isEmpty()) {
                return err("aldi",
                        "Aldi page structure could not be parsed. Their catalogues are primarily image-based.",
                        "https://www.aldi.com.au/en/specials/this-weeks-specials/");
            }

            return ok("aldi", products, 1, products.size());

        } catch (Exception e) {
            return err("aldi",
                    "Could not reach Aldi website: " + simplify(e.getMessage()),
                    "https://www.aldi.com.au/en/specials/this-weeks-specials/");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String text(JsonNode node, String... paths) {
        for (String path : paths) {
            try {
                JsonNode n = node;
                for (String part : path.split("\\.")) {
                    if (part.endsWith("]")) {
                        String field = part.substring(0, part.indexOf('['));
                        int idx = Integer.parseInt(part.substring(part.indexOf('[') + 1, part.indexOf(']')));
                        n = n.path(field).path(idx);
                    } else {
                        n = n.path(part);
                    }
                }
                if (!n.isMissingNode() && !n.isNull() && !n.asText("").isBlank()) return n.asText("");
            } catch (Exception ignored) {}
        }
        return "";
    }

    private double price(JsonNode node, String... paths) {
        for (String path : paths) {
            try {
                JsonNode n = node;
                for (String part : path.split("\\.")) n = n.path(part);
                if (!n.isMissingNode() && !n.isNull()) return n.asDouble(0);
            } catch (Exception ignored) {}
        }
        return 0.0;
    }

    private double parsePrice(String text) {
        if (text == null || text.isBlank()) return 0.0;
        String cleaned = text.replaceAll("[^0-9.]", "");
        try { return Double.parseDouble(cleaned); } catch (Exception e) { return 0.0; }
    }

    private String simplify(String msg) {
        if (msg == null) return "unknown error";
        if (msg.length() > 120) msg = msg.substring(0, 120) + "…";
        return msg.replace("\"", "'");
    }
}
