package com.typeahead.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Application Configuration
 *
 * Configures CORS to allow the React frontend (port 3000) to communicate
 * with the Spring Boot backend (port 8080).
 *
 * Design Decision:
 * - CORS is configured at the application level rather than per-controller
 *   for consistency and centralized security management.
 * - Only /api/** paths are exposed to cross-origin requests.
 * - In production, this would be handled by a reverse proxy (nginx/ALB)
 *   and CORS would be restricted to specific domains.
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600); // Cache preflight response for 1 hour
    }
}
