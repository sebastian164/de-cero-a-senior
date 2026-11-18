package acortador;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Clics de forma asíncrona: la redirección solo incrementa un contador en memoria; un proceso agrupa y escribe en
 * la base cada 500 ms (una escritura por código, no por clic). Registrar y retirar son operaciones atómicas por
 * clave (merge/remove de ConcurrentHashMap): un clic cae o en el volcado en curso o en el siguiente, nunca en
 * ninguno. Si el UPDATE falla, lo retirado se devuelve al mapa. Compromiso: si la instancia cae, se pierden como máximo
 * los clics de los últimos 500 ms.
 */
@Component
public class ContadorDeClics {
    private final Map<String, Long> pendientes = new ConcurrentHashMap<>();
    private final JdbcClient jdbc;

    public ContadorDeClics(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(String codigo) {
        pendientes.merge(codigo, 1L, Long::sum);
    }

    @Scheduled(fixedDelay = 500)
    @PreDestroy
    public void volcar() {
        for (String codigo : pendientes.keySet()) {
            Long n = pendientes.remove(codigo);
            if (n == null || n == 0) continue;
            try {
                jdbc.sql("UPDATE urls SET clics = clics + :n WHERE codigo = :c").param("n", n).param("c", codigo).update();
            } catch (RuntimeException e) {
                pendientes.merge(codigo, n, Long::sum); // no se pierde: se reintenta en el próximo volcado
            }
        }
    }
}
