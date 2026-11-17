package inventario;

import static inventario.Entorno.B;
import static inventario.Entorno.evento;
import static inventario.Entorno.publicar;
import static inventario.Entorno.stock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import comun.Ev;
import comun.Kf;
import inventario.configuracion.RetrasoDelGrupo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.context.ConfigurableApplicationContext;
import pruebas.Defectos;
import pruebas.ListenerAntes;
import pruebas.Reintentos;

/** Pruebas de integración con un Kafka real (broker del compose) y PostgreSQL real. Pasos 1, 3 a 11 del taller. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventarioIntegracionTest {

    static final Class<?>[] CON_DEFECTOS = {Aplicacion.class, Defectos.Decorador.class};
    static final Class<?>[] ANTES = {Aplicacion.class, ListenerAntes.class, Defectos.Decorador.class};

    @BeforeEach
    void limpio() throws Exception {
        Entorno.reiniciar();
        Defectos.LLAMADAS.set(0);
        Defectos.pausaMs = 0;
        Defectos.pedidoQueExplota = null;
    }

    static List<String> tipos(String tema) {
        return Kf.leerTodo(tema).stream().map(r -> r.value().replaceAll(".*\"tipo\":\"(\\w+)\".*", "$1")).collect(Collectors.toList());
    }

    // ------------------------------------------------------------ paso 1 y 9a
    @Test
    @Order(1)
    void reservaYPublicaElResultado() throws Exception {
        try (ConfigurableApplicationContext ctx = Entorno.arrancar()) {
            publicar("pedidos", "ped-1", evento("e1", "ped-1", "producto-1", 3));
            publicar("pedidos", "ped-2", evento("e2", "ped-2", "producto-3", 9));    // solo hay 5
            Kf.esperar(() -> Kf.contar("inventario.eventos") == 2, 60_000, "dos resultados");
        }
        Ev.nota("1/9a. producto-1: 1000 -> " + stock("producto-1") + " (reservó 3); producto-3 sin tocar: " + stock("producto-3") + "; resultados publicados: " + tipos("inventario.eventos").stream().sorted().toList());
        assertEquals(997, stock("producto-1"));
        assertEquals(5, stock("producto-3"));
        assertEquals(List.of("InventarioInsuficiente", "InventarioReservado"), tipos("inventario.eventos").stream().sorted().toList());
    }

    // ------------------------------------------------------------ paso 3
    @Test
    @Order(2)
    void elMismoEventoTresVecesDescuentaUnaVez() throws Exception {
        try (ConfigurableApplicationContext ctx = Entorno.arrancar()) {
            for (int i = 0; i < 3; i++) publicar("pedidos", "ped-1", evento("e1", "ped-1", "producto-1", 3));
            Kf.esperar(() -> Kf.contar("inventario.eventos") == 3, 60_000, "tres resultados");
        }
        Ev.nota("3. el mismo evento (id e1) publicado 3 veces: producto-1 = " + stock("producto-1") + " (un solo descuento de 3), eventos procesados registrados: " + Entorno.procesados());
        assertEquals(997, stock("producto-1"));
        assertEquals(1, Entorno.procesados());
    }

    // ------------------------------------------------------------ pasos 4 y 5
    @Test
    @Order(3)
    void paso5_tresClasesDeFalloSinPerderNada() throws Exception {
        try (ConfigurableApplicationContext ctx = Entorno.arrancar(CON_DEFECTOS, "--inventario.concurrency=1")) {
            // (a) formato inválido: directo a fallidos, sin reintentos; el siguiente se procesa
            long t0 = System.currentTimeMillis();
            publicar("pedidos", "ped-a", "{esto no es json");
            publicar("pedidos", "ped-b", evento("eb", "ped-b", "producto-1", 1));
            Kf.esperar(() -> Kf.contar("pedidos.fallidos") == 1 && stock("producto-1") == 999, 30_000, "inválido a fallidos y el siguiente procesado");
            Ev.nota("5a. formato inválido: en fallidos tras " + (System.currentTimeMillis() - t0) + " ms (sin reintentos); el siguiente mensaje se procesó (producto-1 = " + stock("producto-1") + ")");
            var fallido = Kf.leerTodo("pedidos.fallidos").get(0);
            Ev.nota("5a. cabeceras del fallido: " + java.util.Arrays.stream(fallido.headers().toArray()).map(h -> h.key()).filter(k -> k.startsWith("kafka_dlt")).sorted().toList());
            assertTrue(fallido.headers().lastHeader("kafka_dlt-exception-message") != null);

            // (b) la base de datos "caída" durante 15 s: reintentos con espera; ningún mensaje a fallidos
            try (Connection bloqueo = Entorno.db()) {
                bloqueo.setAutoCommit(false);
                try (Statement st = bloqueo.createStatement()) {
                    st.execute("LOCK TABLE k06.stock IN EXCLUSIVE MODE");
                }
                for (int i = 0; i < 5; i++) publicar("pedidos", "ped-c" + i, evento("ec" + i, "ped-c" + i, "producto-2", 2));
                Thread.sleep(15_000);
                assertEquals(1000, stock("producto-2") + 0 * 1, "lectura sin bloqueo: la tabla está bloqueada para escritura, no para SELECT");
                bloqueo.rollback();
            }
            Kf.esperar(() -> stock("producto-2") == 990, 60_000, "los cinco se aplican al volver la base");
            Ev.nota("5b. base bloqueada 15 s con 5 eventos publicados: al volver, producto-2 = " + stock("producto-2") + " (5 x 2 descontados); fallidos: " + Kf.contar("pedidos.fallidos") + " (solo el inválido de antes)");
            assertEquals(1, Kf.contar("pedidos.fallidos"));

            // (c) error de programación: NullPointerException al publicar el resultado de un pedido
            Defectos.pedidoQueExplota = "boom";
            int antes = Defectos.LLAMADAS.get();
            publicar("pedidos", "boom-1", evento("ed", "boom-1", "producto-1", 1));
            publicar("pedidos", "boom-2", evento("ee", "boom-2", "producto-1", 1));
            Kf.esperar(() -> Kf.contar("pedidos.fallidos") == 3, 90_000, "el error de programación acaba en fallidos");
            var causas = Kf.leerTodo("pedidos.fallidos").stream().map(r -> new String(r.headers().lastHeader("kafka_dlt-exception-cause-fqcn") != null ? r.headers().lastHeader("kafka_dlt-exception-cause-fqcn").value() : r.headers().lastHeader("kafka_dlt-exception-fqcn").value())).sorted().toList();
            Ev.nota("5c. NullPointerException inesperada: " + (Defectos.LLAMADAS.get() - antes) + " ejecuciones del caso de uso (2 pedidos x (1 + reintentos)) y luego a fallidos; excepciones registradas en fallidos: " + causas);
            assertTrue(causas.stream().anyMatch(c -> c.contains("NullPointerException")), causas.toString());
            assertEquals(997, stock("producto-1"), "boom-1 y boom-2: el efecto en la base se aplicó una sola vez cada uno (999 - 2) aunque el mensaje se reintentara 8 veces");
        }
    }

    // ------------------------------------------------------------ paso 6
    @Test
    @Order(4)
    void paso6_elCatchQueNoRelanzaPierdeMensajes() throws Exception {
        try (ConfigurableApplicationContext ctx = Entorno.arrancar(ANTES, "--inventario.listener=antes", "--inventario.concurrency=1")) {
            publicar("pedidos", "ped-a", "{esto no es json");
            try (Connection bloqueo = Entorno.db()) {
                bloqueo.setAutoCommit(false);
                try (Statement st = bloqueo.createStatement()) {
                    st.execute("LOCK TABLE k06.stock IN EXCLUSIVE MODE");
                }
                for (int i = 0; i < 5; i++) publicar("pedidos", "ped-c" + i, evento("ec" + i, "ped-c" + i, "producto-2", 2));
                Thread.sleep(8_000);
                bloqueo.rollback();
            }
            Defectos.pedidoQueExplota = "boom";
            publicar("pedidos", "boom-1", evento("ed", "boom-1", "producto-1", 1));
            publicar("pedidos", "ped-ok", evento("ef", "ped-ok", "producto-1", 1));
            Kf.esperar(() -> Kf.contar("inventario.eventos") >= 1 && stock("producto-1") <= 998, 60_000, "el último se procesa");
            Thread.sleep(3000);
            var reg = new SimpleMeterRegistry();
            double retraso;
            try (Admin admin = Kf.admin(B)) {
                new RetrasoDelGrupo(admin, "inventario", "pedidos", reg);
                retraso = reg.get("kafka.consumer.group.lag").gauge().value();
            }
            Ev.nota("6. con el catch que no relanza: 8 mensajes publicados (1 inválido, 5 con la base caída, 1 con error de programación, 1 bueno). producto-2 = " + stock("producto-2")
                    + " (los 5 de la base caída no se aplicaron); producto-1 = " + stock("producto-1") + " (solo el bueno se aplicó; el del error de programación se perdió: su descuento sí se hizo antes de fallar la publicación, "
                    + "pero el resultado no se publicó y nadie se enteró); fallidos: " + Kf.contar("pedidos.fallidos") + "; retraso del grupo: " + retraso + " (todo en verde)");
            assertEquals(0.0, retraso);
            assertEquals(1000, stock("producto-2"), "los cinco de la base caída se perdieron");
            assertEquals(0, Kf.contar("pedidos.fallidos"), "y nada llegó a fallidos: pérdida silenciosa");
        }
    }

    // ------------------------------------------------------------ paso 7
    @Test
    @Order(5)
    void paso7_concurrenciaSeisConTresParticiones() throws Exception {
        long[] ms = new long[3];
        int[] conc = {1, 3, 6};
        Defectos.pausaMs = 20;
        for (int k = 0; k < 3; k++) {
            Entorno.reiniciar();
            try (var p = new org.apache.kafka.clients.producer.KafkaProducer<String, String>(Map.of("bootstrap.servers", B,
                    "key.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getName(),
                    "value.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getName()))) {
                for (int i = 0; i < 300; i++) p.send(new org.apache.kafka.clients.producer.ProducerRecord<>("pedidos", "ped-" + i, evento("c" + k + "-" + i, "ped-" + i, "producto-1", 1)));
                p.flush();
            }
            try (ConfigurableApplicationContext ctx = Entorno.arrancar(CON_DEFECTOS, "--inventario.concurrency=" + conc[k])) {
                long t0 = System.currentTimeMillis();
                if (conc[k] == 6) {
                    try (Admin admin = Kf.admin(B)) {
                        Kf.esperar(() -> admin.describeConsumerGroups(List.of("inventario")).all().get().get("inventario").members().size() == 6, 60_000, "seis miembros");
                        var miembros = admin.describeConsumerGroups(List.of("inventario")).all().get().get("inventario").members();
                        Ev.nota("7. concurrency=6, tres particiones: " + miembros.size() + " miembros, particiones por miembro " + miembros.stream().map(m -> m.assignment().topicPartitions().size()).sorted().toList() + " (tres hilos ociosos)");
                        assertEquals(3, miembros.stream().filter(m -> m.assignment().topicPartitions().isEmpty()).count());
                    }
                }
                Kf.esperar(() -> stock("producto-1") == 700, 120_000, "los 300");
                ms[k] = System.currentTimeMillis() - t0;
            }
        }
        Ev.nota("7. 300 eventos a 20 ms de trabajo cada uno: concurrency=1 -> " + ms[0] + " ms; 3 -> " + ms[1] + " ms; 6 -> " + ms[2] + " ms");
        assertTrue(ms[1] < ms[0] * 0.6, "tres hilos son claramente más rápidos que uno");
        assertTrue(ms[2] > ms[1] * 0.7, "seis hilos con tres particiones no mejoran a tres");
    }

    // ------------------------------------------------------------ paso 8
    @Test
    @Order(6)
    void paso8_reintentosNoBloqueantesCambianElOrden() throws Exception {
        Kf.tema(B, "reintentos-bloqueantes", 1, 1, Map.of());
        Kf.tema(B, "reintentos-no-bloqueantes", 1, 1, Map.of());
        for (Class<?> cfg : new Class<?>[] {Reintentos.Bloqueante.class, Reintentos.NoBloqueante.class}) {
            boolean bloq = cfg == Reintentos.Bloqueante.class;
            Reintentos.ORDEN.clear();
            java.lang.reflect.Field f = Reintentos.class.getDeclaredField("INTENTOS");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicInteger) f.get(null)).set(0);
            String tema = bloq ? "reintentos-bloqueantes" : "reintentos-no-bloqueantes";
            try (ConfigurableApplicationContext ctx = Entorno.arrancar(new Class<?>[] {cfg})) {
                publicar(tema, "misma-clave", "a1-primero");
                publicar(tema, "misma-clave", "a2-segundo");
                Kf.esperar(() -> Reintentos.ORDEN.size() == 2, 60_000, "los dos mensajes");
            }
            Ev.nota("8. reintentos " + (bloq ? "bloqueantes" : "no bloqueantes") + ": orden de proceso con la misma clave y el primero fallando una vez -> " + Reintentos.ORDEN);
            assertEquals(bloq ? List.of("a1-primero", "a2-segundo") : List.of("a2-segundo", "a1-primero"), Reintentos.ORDEN);
        }
    }

    // ------------------------------------------------------------ paso 9b
    @Test
    @Order(7)
    void paso9b_defectuosoAFallidosYElSiguienteSeProcesa() throws Exception {
        try (ConfigurableApplicationContext ctx = Entorno.arrancar("--inventario.concurrency=1")) {
            publicar("pedidos", "ped-x", "{\"id\":\"x\"}");                               // JSON válido, pero sin pedidoId ni líneas
            publicar("pedidos", "ped-y", evento("ey", "ped-y", "producto-2", 4));
            Kf.esperar(() -> Kf.contar("pedidos.fallidos") == 1 && stock("producto-2") == 996, 30_000, "fallido y siguiente");
            Ev.nota("9b. evento que incumple el contrato -> fallidos (1); el siguiente se procesó: producto-2 = " + stock("producto-2"));
        }
    }

    // ------------------------------------------------------------ paso 10
    @Test
    @Order(8)
    void paso10_apagadoOrdenadoNoReprocesaMasDeUnMensaje() throws Exception {
        try (var p = new org.apache.kafka.clients.producer.KafkaProducer<String, String>(Map.of("bootstrap.servers", B,
                "key.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getName(),
                "value.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getName()))) {
            for (int i = 0; i < 300; i++) p.send(new org.apache.kafka.clients.producer.ProducerRecord<>("pedidos", "ped-" + i, evento("d" + i, "ped-" + i, "producto-1", 1)));
            p.flush();
        }
        Defectos.pausaMs = 20;
        try (ConfigurableApplicationContext ctx = Entorno.arrancar(CON_DEFECTOS, "--inventario.concurrency=3")) {
            Kf.esperar(() -> stock("producto-1") <= 940, 120_000, "unos 60 procesados");
        }                                                                                     // close(): apagado ordenado del contenedor
        int hechosAlCerrar = 1000 - stock("producto-1");
        int ejecuciones1 = Defectos.LLAMADAS.get();
        Defectos.pausaMs = 0;
        try (ConfigurableApplicationContext ctx = Entorno.arrancar(CON_DEFECTOS, "--inventario.concurrency=3")) {
            Kf.esperar(() -> stock("producto-1") == 700, 120_000, "todos");
        }
        int ejecuciones = Defectos.LLAMADAS.get();
        int reprocesados = ejecuciones - 300;
        Ev.nota("10. despliegue a mitad de 300 eventos: al cerrar llevaba " + hechosAlCerrar + " descontados (" + ejecuciones1 + " ejecuciones); tras reiniciar, ejecuciones totales " + ejecuciones + " -> reprocesados " + reprocesados
                + "; descuentos efectivos " + (1000 - stock("producto-1")) + " de 300 (idempotente)");
        assertTrue(reprocesados <= 3, "con ack-mode record, como mucho el mensaje en curso de cada hilo se repite (3 hilos): " + reprocesados);
        assertEquals(700, stock("producto-1"));
    }

    // ------------------------------------------------------------ paso 11
    @Test
    @Order(9)
    void paso11_retrasoDelGrupoComoMetrica() throws Exception {
        try (ConfigurableApplicationContext ctx = Entorno.arrancar("--inventario.concurrency=1")) {
            publicar("pedidos", "ped-0", evento("g0", "ped-0", "producto-1", 1));
            Kf.esperar(() -> stock("producto-1") == 999, 30_000, "el primero");
            Thread.sleep(1000);
        }
        for (int i = 1; i <= 40; i++) publicar("pedidos", "ped-" + i, evento("g" + i, "ped-" + i, "producto-1", 1));     // con el servicio apagado
        var registro = new SimpleMeterRegistry();
        try (Admin admin = Kf.admin(B)) {
            var medidor = new RetrasoDelGrupo(admin, "inventario", "pedidos", registro);
            double retraso = registro.get("kafka.consumer.group.lag").tag("group", "inventario").gauge().value();
            java.lang.ref.Reference.reachabilityFence(medidor);
            Ev.nota("11. métrica kafka.consumer.group.lag{group=inventario} con el servicio apagado y 40 eventos publicados: " + retraso);
            assertEquals(40.0, retraso);
        }
        try (ConfigurableApplicationContext ctx = Entorno.arrancar("--inventario.concurrency=1")) {
            // la métrica la expone el propio servicio: se lee del MeterRegistry de su contexto
            var registro2 = ctx.getBean(io.micrometer.core.instrument.MeterRegistry.class);
            Kf.esperar(() -> registro2.get("kafka.consumer.group.lag").tag("group", "inventario").gauge().value() == 0.0, 60_000, "retraso 0");
            Ev.nota("11. con el servicio de vuelta, el retraso (leído del MeterRegistry del servicio) baja a 0");
        }
    }
}
