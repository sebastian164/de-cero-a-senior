package inventario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import comun.Ev;
import comun.Kf;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Paso 9 del taller, tal como lo pide el enunciado: pruebas de integración con Testcontainers. Cada ejecución crea SU Kafka (apache/kafka:4.3.1, la
 * imagen del entorno del curso) y SU PostgreSQL 17, sin tocar el entorno compartido. Testcontainers elige puertos libres del anfitrión.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContenedoresTest {

    static KafkaContainer kafka;
    static PostgreSQLContainer postgres;
    static String bootstrap;
    static String jdbc;

    @BeforeAll
    static void arrancarContenedores() throws Exception {
        kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withDatabaseName("taller").withUsername("taller").withPassword("taller");
        kafka.start();
        postgres.start();
        bootstrap = kafka.getBootstrapServers();
        jdbc = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/taller";       // sin los parámetros que añade getJdbcUrl(): aquí se pone currentSchema
        for (String t : List.of("pedidos", "pedidos.fallidos", "inventario.eventos")) Kf.tema(bootstrap, t, 3, 1, Map.of());
        try (Connection c = DriverManager.getConnection(jdbc, "taller", "taller"); Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA k06");
            st.execute("CREATE TABLE k06.stock(producto_id text PRIMARY KEY, existencias int NOT NULL CHECK (existencias >= 0))");
            st.execute("CREATE TABLE k06.procesados(evento_id text PRIMARY KEY, resultado text)");
            st.execute("INSERT INTO k06.stock VALUES ('producto-1', 1000)");
        }
    }

    @AfterAll
    static void pararContenedores() {
        if (kafka != null) kafka.stop();
        if (postgres != null) postgres.stop();
    }

    static int stock() throws Exception {
        try (Connection c = DriverManager.getConnection(jdbc, "taller", "taller"); Statement st = c.createStatement(); var rs = st.executeQuery("SELECT existencias FROM k06.stock WHERE producto_id = 'producto-1'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    static void publicar(String clave, String valor) throws Exception {
        try (var p = new KafkaProducer<String, String>(Map.of("bootstrap.servers", bootstrap, "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName(), "acks", "all"))) {
            p.send(new ProducerRecord<>("pedidos", clave, valor)).get();
        }
    }

    static ConfigurableApplicationContext servicio() {
        return SpringApplication.run(new Class<?>[] {Aplicacion.class}, new String[] {"--spring.kafka.bootstrap-servers=" + bootstrap,
                "--spring.datasource.url=" + jdbc + "?currentSchema=k06", "--spring.datasource.username=taller", "--spring.datasource.password=taller"});
    }

    @Test
    @Order(1)
    void publicaUnEventoYSeReserva() throws Exception {
        try (ConfigurableApplicationContext ctx = servicio()) {
            publicar("ped-1", Entorno.evento("tc-1", "ped-1", "producto-1", 4));
            Kf.esperar(() -> Kf.contar(bootstrap, "inventario.eventos") == 1, 90_000, "el resultado publicado");
        }
        Ev.nota("TC1. Testcontainers (apache/kafka:4.3.1 y postgres:17): un evento publicado -> producto-1 de 1000 a " + stock() + " y un InventarioReservado en inventario.eventos");
        assertEquals(996, stock());
    }

    @Test
    @Order(2)
    void unEventoInvalidoVaAFallidosYElSiguienteSeProcesa() throws Exception {
        try (ConfigurableApplicationContext ctx = servicio()) {
            publicar("malo", "esto no es json");
            publicar("ped-2", Entorno.evento("tc-2", "ped-2", "producto-1", 6));
            Kf.esperar(() -> Kf.contar(bootstrap, "pedidos.fallidos") == 1 && stock() == 990, 90_000, "inválido a fallidos y el siguiente procesado");
        }
        Ev.nota("TC2. Testcontainers: un mensaje inválido llegó a pedidos.fallidos (" + Kf.contar(bootstrap, "pedidos.fallidos") + ") y el siguiente se procesó: producto-1 = " + stock());
        assertEquals(990, stock());
    }
}
