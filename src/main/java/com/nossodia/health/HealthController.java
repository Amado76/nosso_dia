package com.nossodia.health;

import com.nossodia.health.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {

    @GetMapping("/api/health")
    @Operation(summary = "Check application responsiveness",
            description = "Requires HTTP Basic authentication. Does not check database or external dependencies.")
    HealthResponse health() {
        return new HealthResponse("UP");
    }
}
