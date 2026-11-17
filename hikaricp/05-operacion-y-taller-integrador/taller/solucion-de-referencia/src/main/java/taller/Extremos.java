package taller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/** Los tres extremos del taller integrador (lectura, escritura, llamada externa) y los dos que se rompen para el simulacro. */
@RestController
public class Extremos {

    private final JdbcTemplate jdbc;
    private final DataSource pool;
    private final RestClient externo;

    public Extremos(JdbcTemplate jdbc, DataSource pool, @Value("${externo.url}") String urlExterno) {
        this.jdbc = jdbc;
        this.pool = pool;
        this.externo = RestClient.create(urlExterno);
    }

    /** Una lectura rápida. */
    @GetMapping("/lectura/{id}")
    public Map<String, Object> lectura(@PathVariable long id) {
        return jdbc.queryForMap("select id, valor from tablero where id = ?", 1 + id % 1000);
    }

    /** Una escritura. */
    @Transactional
    @PostMapping("/escritura/{id}")
    public String escritura(@PathVariable long id) {
        jdbc.update("update tablero set valor = valor + 1 where id = ?", 1 + id % 1000);
        return "ok";
    }

    /** Una operación que llama a un servicio externo con la conexión en la mano (el defecto de la sesión 04, a propósito). */
    @Transactional
    @PostMapping("/externo/{id}")
    public String conLlamadaExterna(@PathVariable long id) {
        jdbc.queryForMap("select id, valor from tablero where id = ?", 1 + id % 1000);
        externo.get().uri("/pagar").retrieve().body(String.class);
        jdbc.update("update tablero set valor = valor + 1 where id = ?", 1 + id % 1000);
        return "pagado";
    }

    /** Una consulta que depende de un índice, para el simulacro de la consulta lenta. */
    @GetMapping("/historial/{cliente}")
    public Map<String, Object> historial(@PathVariable long cliente) {
        return jdbc.queryForMap("select count(*) as compras, coalesce(sum(importe), 0) as importe from historial where cliente_id = ?", cliente);
    }

    /** La fuga: con un id negativo toma la conexión a mano y sale sin cerrarla. */
    @GetMapping("/fuga/{id}")
    public String fuga(@PathVariable long id) throws SQLException {
        Connection conexion = pool.getConnection();
        PreparedStatement sentencia = conexion.prepareStatement("select valor from tablero where id = ?");
        sentencia.setLong(1, 1 + Math.abs(id) % 1000);
        try (ResultSet fila = sentencia.executeQuery()) {
            fila.next();
        }
        if (id < 0) {
            return "camino raro";                                // sin close(): la fuga
        }
        sentencia.close();
        conexion.close();
        return "ok";
    }
}
