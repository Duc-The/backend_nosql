package vn.ducthe.organic.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;
import vn.ducthe.organic.entity.AccountEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.service.impl.RoleAccess;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtSecret(@Value("${app.jwt.secret}") String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("app.jwt.secret must contain at least 32 Base64-encoded bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecret));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecret, AuthStore store, @Value("${app.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecret).macAlgorithm(MacAlgorithm.HS256).build();
        var standard = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(jwt -> {
            var result = standard.validate(jwt);
            if (result.hasErrors()) {
                return result;
            }
            AccountEntity account = jwt.getSubject() == null ? null : store.activeAccount(jwt.getSubject());
            if (account != null && jwt.getExpiresAt() != null
                    && jwt.getExpiresAt().isAfter(java.time.Instant.now())
                    && AuthStore.credentialVersion(account).equals(jwt.getClaimAsString("ver"))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        });
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-origin:http://localhost:5173}") String frontendOrigin) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(frontendOrigin));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder, RoleAccess roles,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
        var unauthorized = (org.springframework.security.web.AuthenticationEntryPoint) (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            resolver.resolveException(request, response, null, new ApiException(ApiError.INVALID_TOKEN));
        };
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/register/resend-otp",
                                "/api/v1/auth/register/verify-otp", "/api/v1/auth/login", "/api/v1/auth/google",
                                "/api/v1/auth/refresh", "/api/v1/auth/forgot-password",
                                "/api/v1/auth/forgot-password/verify-otp", "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/categories",
                                "/api/v1/catalog/products", "/api/v1/catalog/products/{slug}", "/api/v1/catalog/products/{slug}/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/vnpay/ipn").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/payos/webhook").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/staff/orders", "/api/v1/staff/orders/**")
                                .hasAnyRole("ADMIN", "SALES_STAFF")
                        .requestMatchers("/api/v1/staff/warehouse/**").hasAnyRole("ADMIN", "WAREHOUSE_STAFF")
                        .requestMatchers("/api/v1/staff/**").denyAll()
                        .requestMatchers("/api/v1/customers/**", "/api/v1/cart", "/api/v1/cart/**",
                                "/api/v1/orders", "/api/v1/orders/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((request, response, exception) -> resolver.resolveException(
                                request, response, null, new ApiException(ApiError.ACCESS_DENIED))))
                .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(unauthorized)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(token ->
                                new JwtAuthenticationToken(token, roles.roles(token.getSubject()).stream()
                                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList()))))
                .build();
    }
}
