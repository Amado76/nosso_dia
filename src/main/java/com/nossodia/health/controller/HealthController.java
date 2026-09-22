package com.nossodia.health.controller;

import com.nossodia.health.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {

    @GetMapping("/api/health")
    @Operation(summary = "Check application responsiveness",
            description = "Public liveness check. Does not check database or external dependencies.")
    HealthResponse health() {
        return new HealthResponse("UP");
    }
}
