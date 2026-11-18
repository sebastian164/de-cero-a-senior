package acortador;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ServicioUrls {
    public static final Pattern ALIAS = Pattern.compile("[A-Za-z0-9_-]{3,16}");
    public record Destino(String urlLarga, Instant expiraEn) {
        boolean vencido(Instant ahora) { return expiraEn != null && !expiraEn.isAfter(ahora); }
    }
    public static class AliasOcupado extends RuntimeException { }
    public static class SolicitudInvalida extends RuntimeException {
        public SolicitudInvalida(String m) { super(m); }
    }

    private final JdbcClient jdbc;
    private final GeneradorDeCodigos generador;
    private final Cache<String, Destino> cache;
    private final Cache<String, Boolean> inexistentes; // caché negativa de vida corta: los "no existe" repetidos no llegan a la base

    public ServicioUrls(JdbcClient jdbc, GeneradorDeCodigos generador, Propiedades p) {
        this.jdbc = jdbc;
        this.generador = generador;
        this.cache = p.cache() ? Caffeine.newBuilder().maximumSize(200_000).expireAfterWrite(Duration.ofMinutes(10)).build() : null;
        this.inexistentes = p.cache() ? Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(Duration.ofSeconds(30)).build() : null;
    }

    public String crear(String url, String alias, Instant expiraEn) {
        validar(url, alias, expiraEn);
        if (alias != null) {
            try {
                insertar(alias, url, expiraEn);
            } catch (DuplicateKeyException e) {
                throw new AliasOcupado();
            }
            return alias;
        }
        for (int intento = 0; intento < 5; intento++) {
            String codigo = generador.siguiente();
            try {
                insertar(codigo, url, expiraEn);
                return codigo;
            } catch (DuplicateKeyException colision) {
                // aleatorio: se reintenta con otro código
            }
        }
        throw new IllegalStateException("no se pudo generar un código único");
    }

    private void insertar(String codigo, String url, Instant expiraEn) {
        jdbc.sql("INSERT INTO urls(codigo, url_larga, expira_en) VALUES (:c, :u, :e)")
                .param("c", codigo).param("u", url).param("e", expiraEn == null ? null : java.sql.Timestamp.from(expiraEn)).update();
        if (inexistentes != null) inexistentes.invalidate(codigo); // después de insertar: así una lectura concurrente no deja un "no existe" viejo
    }

    /** Devuelve la URL larga o vacío si no existe o expiró. */
    public Optional<String> resolver(String codigo) {
        Instant ahora = Instant.now();
        if (inexistentes != null && inexistentes.getIfPresent(codigo) != null) return Optional.empty();
        Destino d = cache != null ? cache.getIfPresent(codigo) : null;
        if (d == null) {
            d = jdbc.sql("SELECT url_larga, expira_en FROM urls WHERE codigo = :c").param("c", codigo)
                    .query((rs, n) -> new Destino(rs.getString(1), rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant()))
                    .optional().orElse(null);
            if (d != null && cache != null) cache.put(codigo, d);
        }
        if (d == null) {
            if (inexistentes != null) inexistentes.put(codigo, Boolean.TRUE);
            return Optional.empty();
        }
        if (d.vencido(ahora)) {
            if (cache != null) cache.invalidate(codigo);
            jdbc.sql("DELETE FROM urls WHERE codigo = :c AND expira_en <= now()").param("c", codigo).update(); // eliminación perezosa
            return Optional.empty();
        }
        return Optional.of(d.urlLarga());
    }

    public Optional<Long> clics(String codigo) {
        return jdbc.sql("SELECT clics FROM urls WHERE codigo = :c").param("c", codigo).query(Long.class).optional();
    }

    private static void validar(String url, String alias, Instant expiraEn) {
        try {
            URI u = URI.create(url);
            if (u.getHost() == null || !(u.getScheme().equals("http") || u.getScheme().equals("https")) || url.length() > 2048) {
                throw new SolicitudInvalida("url inválida");
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SolicitudInvalida("url inválida");
        }
        if (alias != null && !ALIAS.matcher(alias).matches()) throw new SolicitudInvalida("alias inválido");
        if (expiraEn != null && !expiraEn.isAfter(Instant.now())) throw new SolicitudInvalida("expiraEn debe ser futuro");
    }
}
