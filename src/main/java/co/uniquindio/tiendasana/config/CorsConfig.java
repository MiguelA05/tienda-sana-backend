package co.uniquindio.tiendasana.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class CorsConfig {

        private final List<String> allowedOrigins;

        public CorsConfig(@Value("${cors.allowed-origins:http://localhost:4200}") String allowedOriginsProperty) {
                this.allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isBlank())
                                .collect(Collectors.toList());
        }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);
                config.setAllowedOrigins(allowedOrigins.isEmpty() ? List.of("http://localhost:4200") : allowedOrigins);

        config.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With", "accept", "Origin",
                "Access-Control-Request-Method", "Access-Control-Request-Headers"
        ));
        config.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin", "Access-Control-Allow-Credentials", "Authorization", "Content-Disposition"
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setMaxAge(3600L);

        source.registerCorsConfiguration("/**", config);
        return source;
    }
}