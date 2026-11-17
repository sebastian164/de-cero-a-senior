package reservas.infraestructura;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import reservas.aplicacion.ServicioReservas.Actor;

import java.io.IOException;
import java.util.List;

/**
 * La API es un servidor de recursos OAuth2: valida tokens JWT (firma y vencimiento). El emisor se configura fuera:
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} o {@code ...public-key-location}. Sin token válido, 401.
 */
@Configuration
public class Seguridad {

    @Bean
    SecurityFilterChain cadena(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/reservas/**").authenticated().anyRequest().permitAll())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()).authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    res.getWriter().write("{\"title\":\"No autenticado\",\"status\":401,\"detail\":\"Falta un token válido\"}");
                }))
                .build();
    }

    /** El actor sale del token: el usuario es el {@code sub} y es administrador si su claim {@code roles} incluye ADMIN. */
    public static Actor actor(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new Actor(jwt.getSubject(), roles != null && roles.contains("ADMIN"));
    }
}
