package inventario;

import comun.Kf;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.Admin;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** Preparación común de las pruebas de integración: temas, base de datos y arranque del servicio. */
final class Entorno {

    static final String B = Kf.bootstrap();

    private Entorno() {
    }

    static Connection db() throws Exception {
        Connection c = DriverManager.getConnection(Kf.pgUrl(), "taller", "taller");
        c.setAutoCommit(true);
        return c;
    }

    /** Estado limpio: temas nuevos, grupo borrado, tablas con tres productos. */
    static void reiniciar() throws Exception {
        try (Admin admin = Kf.admin(B)) {
            for (int i = 0; i < 30; i++) {
                try {
                    admin.deleteConsumerGroups(List.of("inventario")).all().get();
                    break;
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("GroupIdNotFound")) break;
                    Thread.sleep(500);
                }
            }
        }
        for (String t : List.of("pedidos", "pedidos.fallidos", "inventario.eventos")) Kf.tema(B, t, 3, 1, Map.of());
        try (Connection c = db(); Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS k06");
            st.execute("DROP TABLE IF EXISTS k06.stock, k06.procesados");
            st.execute("CREATE TABLE k06.stock(producto_id text PRIMARY KEY, existencias int NOT NULL CHECK (existencias >= 0))");
            st.execute("CREATE TABLE k06.procesados(evento_id text PRIMARY KEY, resultado text)");
            st.execute("INSERT INTO k06.stock VALUES ('producto-1', 1000), ('producto-2', 1000), ('producto-3', 5)");
        }
    }

    static ConfigurableApplicationContext arrancar(Class<?>[] fuentes, String... propiedades) {
        List<String> args = new ArrayList<>(List.of("--spring.kafka.bootstrap-servers=" + B, "--spring.datasource.url=" + Kf.pgUrl().replace("/taller", "/taller?currentSchema=k06&options=-c%20lock_timeout%3D1000")));
        args.addAll(List.of(propiedades));
        return SpringApplication.run(fuentes, args.toArray(String[]::new));
    }

    static ConfigurableApplicationContext arrancar(String... propiedades) {
        return arrancar(new Class<?>[] {Aplicacion.class}, propiedades);
    }

    static int stock(String producto) throws Exception {
        try (Connection c = db(); Statement st = c.createStatement(); var rs = st.executeQuery("SELECT existencias FROM k06.stock WHERE producto_id = '" + producto + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    static int procesados() throws Exception {
        try (Connection c = db(); Statement st = c.createStatement(); var rs = st.executeQuery("SELECT count(*) FROM k06.procesados")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    static String evento(String id, String pedido, String producto, int cantidad) {
        return "{\"id\":\"" + id + "\",\"pedidoId\":\"" + pedido + "\",\"lineas\":[{\"productoId\":\"" + producto + "\",\"cantidad\":" + cantidad + "}]}";
    }

    static void publicar(String tema, String clave, String valor) throws Exception {
        try (var p = new org.apache.kafka.clients.producer.KafkaProducer<String, String>(Map.of("bootstrap.servers", B,
                "key.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getName(),
                "value.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getName(), "acks", "all"))) {
            p.send(new org.apache.kafka.clients.producer.ProducerRecord<>(tema, clave, valor)).get();
        }
    }
}
