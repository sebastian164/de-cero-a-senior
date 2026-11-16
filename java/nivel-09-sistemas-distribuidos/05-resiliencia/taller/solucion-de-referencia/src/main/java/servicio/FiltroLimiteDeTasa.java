package servicio;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Límite de tasa por cliente (cabecera X-Cliente): 5 peticiones cada 2 s; el exceso recibe 429 con Retry-After. */
@Component
public class FiltroLimiteDeTasa extends OncePerRequestFilter {

    static final Duration PERIODO = Duration.ofSeconds(2);
    static final int MAXIMO_POR_DEFECTO = 10_000;
    private static final RateLimiterConfig CONFIG = RateLimiterConfig.custom()
            .limitForPeriod(5).limitRefreshPeriod(PERIODO).timeoutDuration(Duration.ZERO).build();

    /** Acotado (LRU): la cabecera X-Cliente la controla quien llama, y un registro sin tope sería una cola sin límite. */
    private final Map<String, RateLimiter> limitadores;
    private final int maximo;

    public FiltroLimiteDeTasa(@org.springframework.beans.factory.annotation.Value("${limite.max-clientes:" + MAXIMO_POR_DEFECTO + "}") int maximo) {
        this.maximo = maximo;
        this.limitadores = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, RateLimiter> e) { return size() > FiltroLimiteDeTasa.this.maximo; }
        });
    }

    public int maximo() { return maximo; }

    public int clientesRegistrados() { return limitadores.size(); }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/limitado/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String cliente = req.getHeader("X-Cliente");
        String nombre = cliente == null ? "anonimo" : cliente;
        RateLimiter limitador = limitadores.computeIfAbsent(nombre, n -> RateLimiter.of(n, CONFIG));
        if (limitador.acquirePermission()) {
            chain.doFilter(req, res);
        } else {
            long espera = PERIODO.toSeconds();                     // la ventana se renueva cada PERIODO
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(espera));
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"DEMASIADAS_PETICIONES\"}");
        }
    }
}
