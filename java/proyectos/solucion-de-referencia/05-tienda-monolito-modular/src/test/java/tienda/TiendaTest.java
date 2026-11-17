package tienda;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import tienda.catalogo.Catalogo;
import tienda.inventario.Inventario;
import tienda.notificaciones.Notificaciones;
import tienda.pagos.Pagos;
import tienda.pagos.interno.PasarelaSimulada;
import tienda.pagos.interno.PasarelaSimulada.Modo;
import tienda.pedidos.interno.Fallos;
import tienda.pedidos.Pedidos;
import tienda.pedidos.Pedidos.Pedido;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** La tienda completa contra PostgreSQL y Kafka reales (contenedores de Testcontainers). */
class TiendaTest {
    static ConfigurableApplicationContext ctx;
    static Pedidos pedidos; static Inventario inventario; static Pagos pagos; static Notificaciones notificaciones;
    static PasarelaSimulada pasarela; static Fallos fallos; static JdbcClient jdbc; static CircuitBreakerRegistry cbs;
    static int n;

    @BeforeAll
    static void arrancar() {
        String base = InfraDePrueba.baseNueva("tienda_pruebas");
        ctx = new SpringApplicationBuilder(AplicacionTienda.class)
                .properties("TIENDA_DB_URL=" + base, "TIENDA_DB_USUARIO=tienda", "TIENDA_DB_CLAVE=tienda",
                        "KAFKA_SERVIDORES=" + InfraDePrueba.KAFKA.getBootstrapServers(), "PUERTO=" + InfraDePrueba.puertoLibre(),
                        "TRAZAS_MUESTREO=0.0", "spring.kafka.consumer.group-id=notificaciones").run();
        pedidos = ctx.getBean(Pedidos.class); inventario = ctx.getBean(Inventario.class); pagos = ctx.getBean(Pagos.class);
        notificaciones = ctx.getBean(Notificaciones.class); pasarela = ctx.getBean(PasarelaSimulada.class); fallos = ctx.getBean(Fallos.class);
        jdbc = ctx.getBean(JdbcClient.class); cbs = ctx.getBean(CircuitBreakerRegistry.class);
    }

    @AfterAll
    static void parar() { if (ctx != null) ctx.close(); }

    @BeforeEach
    void reiniciar() {
        pasarela.modo = Modo.OK; fallos.pasoQueFalla = null; pasarela.llamadas.set(0); pasarela.reembolsos.set(0);
        cbs.circuitBreaker("pasarela").reset();
    }

    String producto(int stock) {
        String sku = "SKU" + (++n);
        ctx.getBean(Catalogo.class).crear(sku, "Producto " + n, new BigDecimal("25.00"));
        inventario.ingresar(sku, stock);
        return sku;
    }

    long eventos(long pedidoId, String tipo) {
        return jdbc.sql("SELECT count(*) FROM eventos_outbox WHERE clave = ? AND tipo = ?").params(String.valueOf(pedidoId), tipo).query(Long.class).single();
    }

    @Test
    void flujo_feliz_reserva_cobra_confirma_y_notifica_una_vez() {
        String sku = producto(5);
        Pedido p = pedidos.confirmar("ana", sku, 2);
        assertThat(p.estado()).isEqualTo("CONFIRMADO");
        assertThat(p.total()).isEqualByComparingTo("50.00");
        assertThat(inventario.disponible(sku)).isEqualTo(3);
        assertThat(pagos.estado(p.id())).isEqualTo("COBRADO");
        await().atMost(Duration.ofSeconds(30)).until(() -> notificaciones.enviadas(p.id()) == 1);
        assertThat(eventos(p.id(), "PedidoConfirmado")).isEqualTo(1);
    }

    @Test
    void sin_stock_rechaza_sin_cobrar() {
        String sku = producto(1);
        Pedido p = pedidos.confirmar("ana", sku, 2);
        assertThat(p.estado()).isEqualTo("RECHAZADO");
        assertThat(p.motivo()).contains("SinStock");
        assertThat(pagos.estado(p.id())).isEqualTo("SIN_COBRO");
        assertThat(pasarela.llamadas.get()).isZero();
        assertThat(inventario.disponible(sku)).isEqualTo(1);
    }

    @Test
    void si_el_cobro_falla_se_libera_el_inventario_tras_reintentar() {
        String sku = producto(5);
        pasarela.modo = Modo.FALLA;
        Pedido p = pedidos.confirmar("ana", sku, 2);
        assertThat(p.estado()).isEqualTo("RECHAZADO");
        assertThat(pasarela.llamadas.get()).isEqualTo(2); // reintento: 2 intentos
        assertThat(inventario.disponible(sku)).isEqualTo(5);
        assertThat(pagos.estado(p.id())).isEqualTo("SIN_COBRO");
        await().atMost(Duration.ofSeconds(30)).until(() -> notificaciones.enviadas(p.id()) == 1);
    }

    @Test
    void tiempo_limite_corta_una_pasarela_lenta() {
        String sku = producto(5);
        pasarela.modo = Modo.LENTA;
        long t0 = System.nanoTime();
        Pedido p = pedidos.confirmar("ana", sku, 1);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertThat(p.estado()).isEqualTo("RECHAZADO");
        assertThat(ms).as("2 intentos de 300 ms, no 2 de 2000 ms").isLessThan(1_500);
        assertThat(inventario.disponible(sku)).isEqualTo(5);
    }

    @Test
    void con_una_pasarela_lenta_el_pedido_rechazado_no_deja_cobro_neto_ni_cobros_duplicados() throws Exception {
        String sku = producto(5);
        pasarela.modo = Modo.LENTA;
        Pedido p = pedidos.confirmar("ana", sku, 1);
        assertThat(p.estado()).isEqualTo("RECHAZADO");
        String clave = Pagos.clave(p.id());
        // La pasarela remota termina sus dos llamadas (una por intento) 2 s después, cuando el cliente ya se fue.
        await().atMost(Duration.ofSeconds(10)).until(() -> pasarela.llamadas.get() == 2);
        Thread.sleep(2_600);
        assertThat(pasarela.cobrosEfectivos(clave)).as("nunca más de un cobro efectivo por pedido").isLessThanOrEqualTo(1);
        assertThat(pasarela.cobroNeto(clave)).as("el cliente no paga un pedido rechazado").isEqualByComparingTo("0");
        assertThat(inventario.disponible(sku)).isEqualTo(5);
    }

    @Test
    void repetir_la_misma_clave_en_la_pasarela_cobra_una_sola_vez_y_el_reembolso_es_idempotente() {
        String clave = "pedido-clave-repetida";
        pasarela.cobrar(clave, new BigDecimal("25.00"));
        pasarela.cobrar(clave, new BigDecimal("25.00"));
        assertThat(pasarela.cobrosEfectivos(clave)).isEqualTo(1);
        assertThat(pasarela.cobroNeto(clave)).isEqualByComparingTo("25.00");
        pasarela.reembolsar(clave);
        pasarela.reembolsar(clave);
        assertThat(pasarela.reembolsos.get()).isEqualTo(1);
        assertThat(pasarela.cobroNeto(clave)).isEqualByComparingTo("0");
    }

    @Test
    void un_pedido_confirmado_tiene_exactamente_un_cobro_efectivo() {
        Pedido p = pedidos.confirmar("ana", producto(5), 2);
        assertThat(p.estado()).isEqualTo("CONFIRMADO");
        assertThat(pasarela.cobrosEfectivos(Pagos.clave(p.id()))).isEqualTo(1);
        assertThat(pasarela.cobroNeto(Pagos.clave(p.id()))).isEqualByComparingTo("50.00");
    }

    @Test
    void el_cortacircuitos_se_abre_deja_de_llamar_y_se_recupera() throws Exception {
        String sku = producto(50);
        pasarela.modo = Modo.FALLA;
        for (int i = 0; i < 2; i++) pedidos.confirmar("ana", sku, 1); // 4 intentos fallidos = ventana completa
        CircuitBreaker cb = cbs.circuitBreaker("pasarela");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int llamadasAntes = pasarela.llamadas.get();
        long t0 = System.nanoTime();
        Pedido p = pedidos.confirmar("ana", sku, 1);
        assertThat(p.estado()).isEqualTo("RECHAZADO");
        assertThat(p.motivo()).contains("CallNotPermitted");
        assertThat(pasarela.llamadas.get()).isEqualTo(llamadasAntes);
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(500);
        assertThat(inventario.disponible(sku)).isEqualTo(50);

        pasarela.modo = Modo.OK;
        Thread.sleep(2_300);
        assertThat(pedidos.confirmar("ana", sku, 1).estado()).isEqualTo("CONFIRMADO");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void un_fallo_en_cualquier_paso_deja_todo_consistente() {
        for (String paso : List.of("reserva", "cobro", "confirmacion")) {
            String sku = producto(5);
            fallos.pasoQueFalla = paso;
            Pedido p = pedidos.confirmar("ana", sku, 2);
            assertThat(p.estado()).as(paso).isEqualTo("RECHAZADO");
            assertThat(p.motivo()).contains("Fallo inyectado en el paso " + paso);
            assertThat(inventario.disponible(sku)).as("stock tras fallo en " + paso).isEqualTo(5);
            assertThat(pagos.estado(p.id())).as("cobro tras fallo en " + paso).isIn("SIN_COBRO", "REEMBOLSADO");
            assertThat(eventos(p.id(), "PedidoConfirmado")).as("sin evento de confirmación").isZero();
            assertThat(eventos(p.id(), "PedidoRechazado")).isEqualTo(1);
        }
        assertThat(pasarela.reembolsos.get()).as("solo el fallo posterior al cobro reembolsa").isEqualTo(1);
    }

    @Test
    void el_consumidor_es_idempotente_ante_entregas_duplicadas() throws Exception {
        KafkaTemplate<String, String> kafka = ctx.getBean(KafkaTemplate.class);
        long pedidoId = 900_000 + n;
        for (int i = 0; i < 3; i++) kafka.send("tienda.eventos", "k", "777777|PedidoConfirmado|" + pedidoId).get();
        await().atMost(Duration.ofSeconds(30)).until(() -> notificaciones.enviadas(pedidoId) >= 1);
        Thread.sleep(1500);
        assertThat(notificaciones.enviadas(pedidoId)).isEqualTo(1);
    }

    @Test
    void treinta_pedidos_simultaneos_por_diez_unidades_no_venden_de_mas() throws Exception {
        String sku = producto(10);
        ExecutorService pool = Executors.newFixedThreadPool(30);
        List<Future<Pedido>> fs = IntStream.range(0, 30).mapToObj(i -> pool.submit(() -> pedidos.confirmar("c" + i, sku, 1))).toList();
        int confirmados = 0;
        for (var f : fs) if (f.get(60, TimeUnit.SECONDS).estado().equals("CONFIRMADO")) confirmados++;
        pool.shutdown();
        assertThat(confirmados).isEqualTo(10);
        assertThat(inventario.disponible(sku)).isZero();
    }

    @Test
    void el_indice_de_pedidos_por_cliente_cambia_el_plan() {
        jdbc.sql("INSERT INTO pedidos_pedidos (cliente, sku, cantidad, total, estado, creado) SELECT 'cli' || (g % 5000), 'X', 1, 10, 'CONFIRMADO', now() - (g || ' seconds')::interval FROM generate_series(1, 200000) g").update();
        String consulta = "EXPLAIN (ANALYZE) SELECT * FROM pedidos_pedidos WHERE cliente = 'cli42' ORDER BY creado DESC LIMIT 20";
        jdbc.sql("DROP INDEX pedidos_por_cliente").update();
        jdbc.sql("ANALYZE pedidos_pedidos").update();
        String sin = String.join("\n", jdbc.sql(consulta).query(String.class).list());
        jdbc.sql("CREATE INDEX pedidos_por_cliente ON pedidos_pedidos (cliente, creado DESC)").update();
        jdbc.sql("ANALYZE pedidos_pedidos").update();
        String con = String.join("\n", jdbc.sql(consulta).query(String.class).list());
        System.out.println("=== SIN INDICE ===\n" + sin + "\n=== CON INDICE ===\n" + con);
        assertThat(sin).contains("Seq Scan").contains("Sort");
        assertThat(con).contains("Index Scan").doesNotContain("Seq Scan").doesNotContain("Sort");
    }
}
