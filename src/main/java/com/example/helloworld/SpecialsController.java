package com.example.helloworld;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/specials")
public class SpecialsController {

    private final SpecialsService service;

    public SpecialsController(SpecialsService service) {
        this.service = service;
    }

    @GetMapping("/woolworths")
    public Map<String, Object> woolworths(@RequestParam(defaultValue = "1") int page) {
        return service.getWoolworths(page);
    }

    @GetMapping("/coles")
    public Map<String, Object> coles(@RequestParam(defaultValue = "1") int page) {
        return service.getColes(page);
    }

    @GetMapping("/aldi")
    public Map<String, Object> aldi() {
        return service.getAldi();
    }
}
