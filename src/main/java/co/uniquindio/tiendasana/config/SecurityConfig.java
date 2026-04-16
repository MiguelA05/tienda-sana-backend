package co.uniquindio.tiendasana.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; // Para @PreAuthorize a nivel de método
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Habilita @PreAuthorize, @PostAuthorize etc.
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource; // Inyectado desde CorsConfig
    private final TokenFilter tokenFilter; // Inyecta tu TokenFilter

    // Constructor para inyección
    public SecurityConfig(CorsConfigurationSource corsConfigurationSource, TokenFilter tokenFilter) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.tokenFilter = tokenFilter; // Asigna el tokenFilter inyectado
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(this.corsConfigurationSource)) // Usa el bean inyectado
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Explícitamente permite OPTIONS globalmente
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/create-account").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/auth/send-recover/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/auth/change-password").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/auth/validate-account").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/auth/resend-validation/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/account/**").authenticated()
                        .requestMatchers("/api/cliente/**").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin.disable()) // Deshabilita form login
                .httpBasic(httpBasic -> httpBasic.disable()); // Deshabilita http basic

        // Añade tu TokenFilter ANTES del filtro estándar de autenticación por username/password
        http.addFilterBefore(this.tokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}