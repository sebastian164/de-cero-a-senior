package pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pedidos.arranque.Aplicacion;
import reactor.blockhound.BlockHound;
import reactor.test.StepVerifier;

/** Extremo a extremo: HTTP real, WebFlux, R2DBC, un PostgreSQL desechable, inventario y pagos simulados con un servidor HTTP que cuenta sus llamadas, y BlockHound. */
@SpringBootTest(classes = Aplicacion.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "20s")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PedidosE2ETest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7");

    static ServiciosSimulados inventario, pagos;

    @DynamicPropertySource static void urls(DynamicPropertyRegistry r) throws Exception {
        inventario = ServiciosSimulados.iniciar("inventario", 0, 50);
        pagos = ServiciosSimulados.iniciar("pagos", 0, 50);
        r.add("inventario.url", () -> "http://localhost:" + inventario.puerto());
        r.add("pagos.url", () -> "http://localhost:" + pagos.puerto());
    }

    @BeforeAll static void blockHound() {
        // Lista de excepciones: VACÍA a propósito. Se probó con las habituales (DNS de Netty, UUID.randomUUID) y esta aplicación no
        // las necesita: las pruebas pasan sin ellas. Si algún día hace falta una, va aquí con su línea de justificación:
        // .allowBlockingCallsInside("clase", "método")   // por qué es inofensivo
        BlockHound.install();
    }

    @Test void blockHoundEstaActivo() {
        StepVerifier.create(reactor.core.publisher.Mono.fromCallable(() -> { java.util.concurrent.locks.LockSupport.parkNanos(10_000_000L); return 1; })
                        .subscribeOn(reactor.core.scheduler.Schedulers.parallel()))
                .expectError(reactor.blockhound.BlockingOperationError.class).verify();
    }

    @AfterAll static void parar() { inventario.parar(); pagos.parar(); }

    @Autowired WebTestClient http;
    @Autowired DatabaseClient db;

    void sql(String s) { db.sql(s).fetch().rowsUpdated().block(); }
    long cantidad(String p) { return db.sql("select cantidad from existencias where producto = '" + p + "'").map(f -> f.get("cantidad", Integer.class)).one().block(); }
    String estado(String id) { return db.sql("select estado from pedido where id = '" + id + "'").map(f -> f.get("estado", String.class)).one().block(); }
    void pedido(String id, String... productos) {
        sql("insert into pedido (id, cliente, estado) values ('" + id + "', 'cliente', 'PENDIENTE')");
        for (String p : productos) sql("insert into existencias values ('" + p + "-" + id + "', 10)");
        for (String p : productos) sql("insert into linea_pedido (pedido_id, producto, cantidad, precio_centavos) values ('" + id + "', '" + p + "-" + id + "', 2, 500)");
    }
    void confirmar(String id, int estado) { http.post().uri("/pedidos/" + id + "/confirmacion").exchange().expectStatus().isEqualTo(estado); }

    @Test void confirmaYDescuenta() {
        pedido("e1", "a", "b");
        http.post().uri("/pedidos/e1/confirmacion").exchange().expectStatus().isOk().expectBody().jsonPath("$.estado").isEqualTo("CONFIRMADO").jsonPath("$.totalCentavos").isEqualTo(2000);
        assertThat(cantidad("a-e1")).isEqualTo(8);
        assertThat(estado("e1")).isEqualTo("CONFIRMADO");
        assertThat(inventario.llamadas("reservar", "e1")).isEqualTo(1);
        assertThat(pagos.llamadas("cobrar", "e1")).isEqualTo(1);
        assertThat(pagos.llamadas("reembolsar", "e1") + inventario.llamadas("liberar", "e1")).isZero();
        http.get().uri("/pedidos/e1").exchange().expectStatus().isOk().expectBody().jsonPath("$.estado").isEqualTo("CONFIRMADO");
    }

    @Test void confirmarDosVecesLaMismaOrdenDescuentaYCobraUnaVez() {
        pedido("e2", "a");
        confirmar("e2", 200);
        http.post().uri("/pedidos/e2/confirmacion").exchange().expectStatus().isEqualTo(422).expectBody().jsonPath("$.detail").value(d -> assertThat((String) d).contains("CONFIRMADO"));
        assertThat(cantidad("a-e2")).isEqualTo(8);
        assertThat(pagos.llamadas("cobrar", "e2")).isEqualTo(1);
    }

    @Test void dosConfirmacionesALaVezSoloUnaGanaYNadieReembolsa() throws Exception {
        pedido("e5", "a", "b");
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<CompletableFuture<Integer>> f = List.of(1, 2).stream().map(i -> CompletableFuture.supplyAsync(() ->
                    http.post().uri("/pedidos/e5/confirmacion").exchange().returnResult(String.class).getStatus().value(), pool)).toList();
            assertThat(f.stream().map(CompletableFuture::join).sorted().toList()).containsExactly(200, 422);
        }
        assertThat(cantidad("a-e5")).isEqualTo(8);
        assertThat(estado("e5")).isEqualTo("CONFIRMADO");
        assertThat(pagos.efectos("cobrar", "e5")).isEqualTo(1);
        assertThat(pagos.efectos("reembolsar", "e5")).as("el perdedor no reembolsa el cobro del ganador").isZero();
        assertThat(inventario.efectos("liberar", "e5")).isZero();
    }

    @Test void sinExistenciasPropiasTrasCobrarSeReembolsaYSeRevierteTodo() {
        pedido("e3", "a", "b", "c");
        sql("update existencias set cantidad = 0 where producto = 'c-e3'");           // el inventario externo dice que sí; la base propia no alcanza
        http.post().uri("/pedidos/e3/confirmacion").exchange().expectStatus().isEqualTo(422).expectHeader().contentType("application/problem+json");
        assertThat(cantidad("a-e3")).isEqualTo(10);
        assertThat(cantidad("b-e3")).isEqualTo(10);
        assertThat(estado("e3")).isEqualTo("PENDIENTE");
        assertThat(pagos.efectos("cobrar", "e3")).isEqualTo(1);
        assertThat(pagos.efectos("reembolsar", "e3")).as("se cobró y se devolvió").isEqualTo(1);
        assertThat(inventario.efectos("liberar", "e3")).isEqualTo(1);
    }

    @Test void pagoRechazadoLiberaLaReservaYNoTocaLaBase() {
        sql("insert into pedido (id, cliente, estado) values ('rechazado-1', 'cliente', 'PENDIENTE')");
        sql("insert into existencias values ('x-rechazado', 10)");
        sql("insert into linea_pedido (pedido_id, producto, cantidad, precio_centavos) values ('rechazado-1', 'x-rechazado', 3, 100)");
        confirmar("rechazado-1", 422);
        assertThat(cantidad("x-rechazado")).isEqualTo(10);
        assertThat(estado("rechazado-1")).isEqualTo("PENDIENTE");
        assertThat(inventario.efectos("liberar", "rechazado-1")).isEqualTo(1);
        assertThat(pagos.llamadas("reembolsar", "rechazado-1")).isZero();
    }

    @Test void inventarioSinExistenciasEs409YNoSeCobra() {
        sql("insert into pedido (id, cliente, estado) values ('sin-stock-1', 'cliente', 'PENDIENTE')");
        sql("insert into linea_pedido (pedido_id, producto, cantidad, precio_centavos) values ('sin-stock-1', 'zz', 1, 100)");
        confirmar("sin-stock-1", 409);
        assertThat(pagos.llamadas("cobrar", "sin-stock-1")).isZero();
    }

    @Test void pedidoInexistenteEs404() { confirmar("no-existe", 404); }

    @Test void elFlujoDeEventosCuentaLaConfirmacion() {
        pedido("e6", "a");
        var eventos = http.get().uri("/pedidos/e6/eventos").accept(MediaType.TEXT_EVENT_STREAM).exchange().expectStatus().isOk().returnResult(String.class).getResponseBody();
        StepVerifier.create(eventos)
                .expectNext("PENDIENTE")                                   // el estado de partida, ya con la suscripción hecha
                .then(() -> confirmar("e6", 200))
                .expectNext("RESERVADO", "COBRADO", "CONFIRMADO")
                .verifyComplete();
    }

    @Test void elFlujoDeEventosCuentaUnPagoRechazado() {
        sql("insert into pedido (id, cliente, estado) values ('rechazado-2', 'cliente', 'PENDIENTE')");
        sql("insert into linea_pedido (pedido_id, producto, cantidad, precio_centavos) values ('rechazado-2', 'zz', 1, 100)");
        var eventos = http.get().uri("/pedidos/rechazado-2/eventos").accept(MediaType.TEXT_EVENT_STREAM).exchange().returnResult(String.class).getResponseBody();
        StepVerifier.create(eventos)
                .expectNext("PENDIENTE")
                .then(() -> confirmar("rechazado-2", 422))
                .expectNext("RESERVADO", "FALLIDO")
                .verifyComplete();
    }

    @Test void unPedidoTerminadoResponderDeInmediatoSuEstadoFinal() {
        pedido("e7", "a");
        confirmar("e7", 200);
        StepVerifier.create(http.get().uri("/pedidos/e7/eventos").accept(MediaType.TEXT_EVENT_STREAM).exchange().returnResult(String.class).getResponseBody())
                .expectNext("CONFIRMADO").verifyComplete();
    }

    @Test @Order(Integer.MAX_VALUE) void unServicioDePagosCaidoEs503YLiberaLaReserva() {
        pedido("e4", "a");
        pagos.parar();                                    // el servicio de pagos se cae
        http.post().uri("/pedidos/e4/confirmacion").exchange().expectStatus().isEqualTo(503).expectHeader().contentType("application/problem+json");
        assertThat(cantidad("a-e4")).isEqualTo(10);       // y no quedó nada a medias
        assertThat(estado("e4")).isEqualTo("PENDIENTE");
        assertThat(inventario.efectos("liberar", "e4")).as("la reserva se devolvió").isEqualTo(1);
    }
}
