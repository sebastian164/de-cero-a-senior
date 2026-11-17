package taller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Los extremos del taller 03. El mismo "buscar cliente" escrito de tres formas, más los casos de los pasos 8 y 9. */
@RestController
public class Clientes {

    private static final String BUSCAR = "select nombre from cliente where id = ?";

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public Clientes(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    /** Paso 1: la conexión se pide a mano y un camino poco frecuente (id negativo) sale sin devolverla. */
    @SuppressWarnings("resource")
    @GetMapping("/fuga/clientes/{id}")
    public String conFuga(@PathVariable long id) throws SQLException {
        Connection conexion = dataSource.getConnection();
        if (id < 0) {
            return "identificador inválido";                     // sale sin cerrar: la conexión no vuelve al pool
        }
        PreparedStatement consulta = conexion.prepareStatement(BUSCAR);
        consulta.setLong(1, id);
        ResultSet fila = consulta.executeQuery();
        String nombre = fila.next() ? fila.getString(1) : "no existe";
        fila.close();
        consulta.close();
        conexion.close();
        return nombre;
    }

    /** Paso 7: try-with-resources. La conexión, la consulta y el resultado se cierran por todos los caminos. */
    @GetMapping("/corregido/clientes/{id}")
    public String corregido(@PathVariable long id) throws SQLException {
        if (id < 0) {
            return "identificador inválido";                     // y esto ni siquiera necesita la base
        }
        try (Connection conexion = dataSource.getConnection();
             PreparedStatement consulta = conexion.prepareStatement(BUSCAR)) {
            consulta.setLong(1, id);
            try (ResultSet fila = consulta.executeQuery()) {
                return fila.next() ? fila.getString(1) : "no existe";
            }
        }
    }

    /** Paso 10: con JdbcTemplate. Aquí no hay ninguna conexión a la vista: la toma y la devuelve la plantilla. */
    @GetMapping("/plantilla/clientes/{id}")
    public String conPlantilla(@PathVariable long id) {
        if (id < 0) {
            return "identificador inválido";
        }
        List<String> nombres = jdbc.queryForList(BUSCAR, String.class, id);
        return nombres.isEmpty() ? "no existe" : nombres.getFirst();
    }

    /**
     * Paso 8: transacción a mano. Los id pares confirman; los impares salen sin confirmar ni revertir,
     * pero cerrando la conexión. La conexión no se fuga: ¿y el update y su bloqueo?
     */
    @GetMapping("/transaccion/clientes/{id}")
    public String transaccionAMedias(@PathVariable long id) throws SQLException {
        try (Connection conexion = dataSource.getConnection()) {
            conexion.setAutoCommit(false);
            try (PreparedStatement visita = conexion.prepareStatement("update cliente set visitas = visitas + 1 where id = ?")) {
                visita.setLong(1, id);
                visita.executeUpdate();
            }
            if (id % 2 == 0) {
                conexion.commit();
                return "confirmada";
            }
            return "sin confirmar ni revertir";                  // el olvido: ni commit ni rollback
        }
    }

    /** Paso 9: una operación legítima que tarda más que el umbral de detección (3 s). Devuelve la conexión al terminar. */
    @GetMapping("/informe")
    public String informeLargo() throws SQLException {
        try (Connection conexion = dataSource.getConnection();
             PreparedStatement consulta = conexion.prepareStatement("select count(*) from cliente, pg_sleep(3)");
             ResultSet fila = consulta.executeQuery()) {
            fila.next();
            return "informe de " + fila.getLong(1) + " clientes";
        }
    }
}
