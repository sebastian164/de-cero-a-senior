package seg;

import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
class SeguridadConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, JwtAuthenticationConverter convertidor) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/productos/**").permitAll()
                        .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/v1/soporte/**").hasAnyRole("SOPORTE", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())      // API sin estado y sin cookies de sesión
                .build();
    }

    /** Los roles viajan en el claim "roles" (lista); en Keycloak se proyectan a ese claim con un mapper. */
    @Bean
    JwtAuthenticationConverter convertidor(@Value("${app.jwt.claim-usuario:sub}") String claimUsuario) {
        var c = new JwtAuthenticationConverter();
        c.setPrincipalClaimName(claimUsuario);
        c.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            Collection<GrantedAuthority> a = (roles == null ? List.<String>of() : roles).stream()
                    .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
            return a;
        });
        return c;
    }

    /** Decodificador con clave pública local: valida firma, expiración, emisor y audiencia. */
    @Bean
    @ConditionalOnProperty("app.jwt.clave-publica-pem")
    JwtDecoder decodificadorLocal(RSAPublicKey clavePublica,
                                  @Value("${app.jwt.emisor}") String emisor,
                                  @Value("${app.jwt.audiencia}") String audiencia) {
        var d = NimbusJwtDecoder.withPublicKey(clavePublica).build();
        OAuth2TokenValidator<Jwt> audiencias = new JwtClaimValidator<List<String>>("aud",
                aud -> aud != null && aud.contains(audiencia));
        d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(emisor), audiencias));
        return d;
    }
}
