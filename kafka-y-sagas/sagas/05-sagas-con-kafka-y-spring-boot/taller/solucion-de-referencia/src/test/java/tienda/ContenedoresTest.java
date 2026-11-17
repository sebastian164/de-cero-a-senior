package tienda;

import static org.junit.jupiter.api.Assertions.assertEquals;

import comun.Ev;
import comun.Kf;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Paso 10 del taller, tal como lo pide el enunciado: pruebas de integración con Testcontainers, un camino feliz y uno de compensación.
 * Cada ejecución crea SU Kafka (apache/kafka:4.3.1) y SU PostgreSQL 17 (con puertos libres del anfitrión) y arranca los cuatro servicios como procesos contra ellos. No toca el entorno compartido.
 */
class ContenedoresTest {

    static KafkaContainer kafka;
    static PostgreSQLContainer postgres;
    static final List<Process> procesos = new ArrayList<>();
    static String JDBC;      // sin los parámetros que añade getJdbcUrl(): aquí se pone currentSchema

    @BeforeAll
    static void arrancar() throws Exception {
        kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withDatabaseName("taller").withUsername("taller").withPassword("taller");
        kafka.start();
        postgres.start();
        JDBC = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/taller";
        for (String t : Mundo.TEMAS) Kf.tema(kafka.getBootstrapServers(), t, 3, 1, Map.of());
        try (Connection c = DriverManager.getConnection(JDBC, "taller", "taller"); Statement st = c.createStatement()) {
            for (String s : Mundo.SERVICIOS) st.execute("CREATE SCHEMA s05_" + s);
        }
        for (String s : Mundo.SERVICIOS) new Configuracion.Esquema(bd(s), s);
        bd("inventario").update("INSERT INTO stock VALUES ('libro', 10)");
        for (String s : Mundo.SERVICIOS) {
            Process p = Kf.hijo(Aplicacion.class, "--tienda.servicio=" + s, "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                    "--spring.datasource.url=" + JDBC + "?currentSchema=s05_" + s);
            Kf.recoger(p);
            procesos.add(p);
        }
        for (String g : Mundo.SERVICIOS) {
            Kf.esperar(() -> {
                try (var a = Kf.admin(kafka.getBootstrapServers())) {
                    return !a.describeConsumerGroups(List.of(g)).all().get().get(g).members().isEmpty();
                }
            }, 120_000, "grupo " + g);
        }
    }

    @AfterAll
    static void parar() throws InterruptedException {
        for (Process p : procesos) if (p.isAlive()) Mundo.matar(p);
        if (kafka != null) kafka.stop();
        if (postgres != null) postgres.stop();
    }

    static JdbcTemplate bd(String servicio) {
        return new JdbcTemplate(new DriverManagerDataSource(JDBC + "?currentSchema=s05_" + servicio, "taller", "taller"));
    }

    static Orquestador cliente() {
        var ds = new DriverManagerDataSource(JDBC + "?currentSchema=s05_orquestador", "taller", "taller");
        return new Orquestador(new JdbcTemplate(ds), new TransactionTemplate(new JdbcTransactionManager(ds)), 3000, 8);
    }

    static String estado(String saga) {
        return bd("orquestador").queryForObject("SELECT estado FROM sagas WHERE id = ?", String.class, saga);
    }

    @Test
    void caminoFelizYCaminoDeCompensacionContraContenedores() {
        Orquestador o = cliente();
        o.iniciar("tc-feliz", 1000, "libro", 1, "Quito");
        o.iniciar("tc-triste", 1000, "libro", 1, "Atlantis");          // envío imposible: reservar, cobrar, reembolsar, liberar
        Kf.esperar(() -> o.noTerminadas().isEmpty(), 120_000, "las dos sagas terminadas");
        long cobrosVigentes = bd("pagos").queryForObject("SELECT count(*) FROM cobros WHERE NOT reembolsado", Long.class);
        long reembolsados = bd("pagos").queryForObject("SELECT count(*) FROM cobros WHERE reembolsado", Long.class);
        long libres = bd("inventario").queryForObject("SELECT disponibles FROM stock WHERE producto = 'libro'", Long.class);
        Ev.nota("TC. Testcontainers (apache/kafka:4.3.1 y postgres:17): tc-feliz " + estado("tc-feliz") + ", tc-triste " + estado("tc-triste")
                + "; cobros vigentes " + cobrosVigentes + ", reembolsados " + reembolsados + ", existencias libres " + libres + " de 10 (una vendida)");
        assertEquals("COMPLETADA", estado("tc-feliz"));
        assertEquals("CANCELADA", estado("tc-triste"));
        assertEquals(1, cobrosVigentes);
        assertEquals(1, reembolsados);
        assertEquals(9, libres);
    }
}
