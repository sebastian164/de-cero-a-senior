package tienda.integrador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import comun.Ev;
import comun.Kf;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tienda.integrador.verificador.Invariantes;

/**
 * El examen contra el sistema levantado con UNA orden (compose/compose.yaml): siete servicios en contenedores, Kafka y PostgreSQL.
 * Se activa con -Dcompose.s07=true (ver el README.md de esta carpeta: primero `docker compose up --build`). Habla solo por HTTP, JDBC y docker.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ComposeTest {

    static final boolean ACTIVA = Boolean.getBoolean("compose.s07");
    static final String PROYECTO = "pub-kafka-y-sagas-s07";
    static final int PEDIDOS = 47701, ORQUESTADOR = 47711;
    static final String PG = "jdbc:postgresql://localhost:47743/taller";
    static final List<String> SERVICIOS = List.of("pedidos", "orquestador", "inventario", "pagos", "envios", "notificaciones", "fidelidad");
    static final List<String> CAMINOS = List.of("feliz", "pago-rechazado", "sin-existencias", "envio-imposible");

    static Map<String, JdbcTemplate> cache;

    /** Una conexión por base, para toda la clase. */
    static synchronized Map<String, JdbcTemplate> bd() {
        if (cache == null) cache = SERVICIOS.stream().collect(Collectors.toMap(s -> s, s -> new JdbcTemplate(new SingleConnectionDataSource(PG + "?currentSchema=s07_" + s, "taller", "taller", true))));
        return cache;
    }

    static void esperarArriba() {
        Kf.esperar(() -> Mundo7.codigo("GET", PEDIDOS, "/metricas", null) == 200 && Mundo7.codigo("GET", ORQUESTADOR, "/metricas", null) == 200, 240_000, "pedidos y orquestador en el compose");
        // los servicios crean sus tablas al arrancar: se espera a las siete bases
        Kf.esperar(() -> {
            Map<String, JdbcTemplate> bd = bd();
            return bd.get("inventario").queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 's07_inventario' AND table_name = 'stock'", Long.class) == 1
                    && bd.get("fidelidad").queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 's07_fidelidad' AND table_name = 'puntos'", Long.class) == 1
                    && bd.get("notificaciones").queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 's07_notificaciones' AND table_name = 'notificaciones'", Long.class) == 1;
        }, 120_000, "tablas de los servicios");
    }

    static void esperarTerminados(Map<String, JdbcTemplate> bd, long ms) {
        Kf.esperar(() -> bd.get("pedidos").queryForObject("SELECT count(*) FROM pedidos WHERE estado = 'PENDIENTE'", Long.class) == 0, ms, "todos los pedidos terminados");
    }

    @Test
    @Order(1)
    void loscuatroCaminosYElVerificadorContraElCompose() {
        assumeTrue(ACTIVA, "requiere el compose levantado (-Dcompose.s07=true)");
        esperarArriba();
        Map<String, JdbcTemplate> bd = bd();
        bd.get("inventario").update("INSERT INTO stock VALUES ('libro', 1000), ('raro', 0) ON CONFLICT DO NOTHING");
        for (int i = 0; i < 20; i++) assertEquals(201, Mundo7.pedir(PEDIDOS, "c-" + i, CAMINOS.get(i % 4)));
        esperarTerminados(bd, 180_000);
        long confirmados = bd.get("pedidos").queryForObject("SELECT count(*) FROM pedidos WHERE estado = 'CONFIRMADO'", Long.class);
        long cancelados = bd.get("pedidos").queryForObject("SELECT count(*) FROM pedidos WHERE estado = 'CANCELADO'", Long.class);
        Kf.esperar(() -> bd.get("notificaciones").queryForObject("SELECT count(*) FROM notificaciones", Long.class) == confirmados + cancelados
                && bd.get("fidelidad").queryForObject("SELECT count(*) FROM puntos", Long.class) == confirmados, 60_000, "notificaciones y puntos");
        List<String> violaciones = Invariantes.verificar(bd, 1000, 600);
        Ev.nota("C1. compose (7 servicios en contenedores): 20 pedidos de los 4 caminos -> " + confirmados + " confirmados, " + cancelados + " cancelados; verificador: " + (violaciones.isEmpty() ? "sin violaciones" : violaciones));
        assertEquals(5, confirmados);
        assertEquals(15, cancelados);
        assertEquals(List.of(), violaciones);
    }

    @Test
    @Order(2)
    void matarElContenedorDePedidosConFilasPendientes() throws Exception {
        assumeTrue(ACTIVA, "requiere el compose levantado (-Dcompose.s07=true)");
        Map<String, JdbcTemplate> bd = bd();
        assertEquals(200, Mundo7.codigo("POST", PEDIDOS, "/admin/publicador/detener", null));
        for (int i = 0; i < 8; i++) assertEquals(201, Mundo7.pedir(PEDIDOS, "k-" + i, CAMINOS.get(i % 4)));
        long pendientes = bd.get("pedidos").queryForObject("SELECT count(*) FROM salida WHERE publicada IS NULL", Long.class);
        Kf.docker("kill", PROYECTO + "-pedidos-1");                                              // SIGKILL al contenedor
        assertTrue(Mundo7.codigo("GET", PEDIDOS, "/metricas", null) != 200, "el contenedor está muerto");
        long trasMorir = bd.get("pedidos").queryForObject("SELECT count(*) FROM salida WHERE publicada IS NULL", Long.class);
        Kf.docker("start", PROYECTO + "-pedidos-1");
        Kf.esperar(() -> Mundo7.codigo("GET", PEDIDOS, "/metricas", null) == 200, 120_000, "Pedidos de vuelta");
        esperarTerminados(bd, 180_000);
        List<String> violaciones = Invariantes.verificar(bd, 1000, 600);
        Ev.nota("C2. docker kill del contenedor de Pedidos con 8 pedidos aceptados y " + pendientes + " filas pendientes (" + trasMorir + " tras morir); al arrancar salieron todas y los 28 pedidos terminaron; verificador: "
                + (violaciones.isEmpty() ? "sin violaciones" : violaciones));
        assertEquals(8, pendientes);
        assertEquals(8, trasMorir);
        assertEquals(List.of(), violaciones);
    }
}
