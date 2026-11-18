package pedidos;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PedidosHttpTest {
    static HttpServer inventario, pagos;

    @BeforeAll static void servicios() throws Exception {
        inventario = ServiciosSimulados.iniciar("inventario", 0, 100);
        pagos = ServiciosSimulados.iniciar("pagos", 0, 100);
    }
    @AfterAll static void parar() { inventario.stop(0); if (pagos != null) pagos.stop(0); }

    @DynamicPropertySource static void urls(DynamicPropertyRegistry r) {
        r.add("inventario.url", () -> "http://localhost:" + inventario.getAddress().getPort());
        r.add("pagos.url", () -> "http://localhost:" + pagos.getAddress().getPort());
    }

    @Autowired WebTestClient web;
    @Autowired EventosDePedido eventos;
    @org.springframework.boot.test.web.server.LocalServerPort int puerto;

    @Test @Order(1) void confirmarOk() {
        web.post().uri("/pedidos/p1/confirmacion").exchange().expectStatus().isOk().expectBody().jsonPath("$.estado").isEqualTo("CONFIRMADO");
        web.get().uri("/pedidos/p1").exchange().expectStatus().isOk().expectBody().jsonPath("$.estado").isEqualTo("CONFIRMADO");
    }

    @Test @Order(2) void inexistenteEs404ConProblemDetail() {
        web.post().uri("/pedidos/no-existe/confirmacion").exchange().expectStatus().isNotFound()
                .expectHeader().contentType("application/problem+json").expectBody().jsonPath("$.status").isEqualTo(404);
    }

    @Test @Order(3) void sinExistenciasEs409() {
        web.post().uri("/pedidos/sin-stock/confirmacion").exchange().expectStatus().isEqualTo(409)
                .expectHeader().contentType("application/problem+json");
    }

    @Test @Order(4) void pagoRechazadoEs422() {
        web.post().uri("/pedidos/rechazado/confirmacion").exchange().expectStatus().isEqualTo(422)
                .expectHeader().contentType("application/problem+json");
    }

    @Test @Order(5) void sseMuestraLosEstadosEnOrden() {
        List<String> recibidos = new CopyOnWriteArrayList<>();
        Flux<String> flujo = org.springframework.web.reactive.function.client.WebClient.create("http://localhost:" + puerto)
                .get().uri("/pedidos/p2/eventos").retrieve().bodyToFlux(String.class).doOnNext(recibidos::add);
        var sub = flujo.subscribe();
        esperarSuscriptor("p2");                                          // la señal: el servidor ya tiene un suscriptor, no un sleep
        web.post().uri("/pedidos/p2/confirmacion").exchange().expectStatus().isOk();
        reactor.test.StepVerifier.create(Flux.interval(Duration.ofMillis(50)).map(i -> List.copyOf(recibidos)).filter(l -> l.size() >= 3).next())
                .assertNext(l -> assertEquals(List.of("RESERVADO", "COBRADO", "CONFIRMADO"), l)).verifyComplete();
        sub.dispose();
    }

    @Test @Order(6) void sseMuestraRESERVADOSeguidoDeFALLIDOConPagoRechazado() {
        List<String> recibidos = new CopyOnWriteArrayList<>();
        var sub = org.springframework.web.reactive.function.client.WebClient.create("http://localhost:" + puerto)
                .get().uri("/pedidos/rechazado/eventos").retrieve().bodyToFlux(String.class).doOnNext(recibidos::add).subscribe();
        esperarSuscriptor("rechazado");
        web.post().uri("/pedidos/rechazado/confirmacion").exchange().expectStatus().isEqualTo(422);
        reactor.test.StepVerifier.create(Flux.interval(Duration.ofMillis(50)).map(i -> List.copyOf(recibidos)).filter(l -> l.size() >= 2).next().timeout(Duration.ofSeconds(5)))
                .assertNext(l -> assertEquals(List.of("RESERVADO", "FALLIDO"), l)).verifyComplete();
        sub.dispose();
    }

    @Test @Order(7) void sinSuscriptoresNoQuedanCanalesAbiertos() {
        var sub = org.springframework.web.reactive.function.client.WebClient.create("http://localhost:" + puerto)
                .get().uri("/pedidos/p4/eventos").retrieve().bodyToFlux(String.class).subscribe();      // nadie confirma p4
        esperarSuscriptor("p4");
        assertEquals(1, eventos.suscriptores("p4"));
        sub.dispose();
        reactor.test.StepVerifier.create(Flux.interval(Duration.ofMillis(50)).filter(i -> eventos.suscriptores("p4") == 0).next().timeout(Duration.ofSeconds(5))).expectNextCount(1).verifyComplete();
        assertEquals(0, eventos.canalesAbiertos(), "al irse el último suscriptor el canal se retira");
    }

    @Test @Order(8) void unPedidoConfirmadoRespondeSuEstadoFinalDeInmediato() {
        web.post().uri("/pedidos/p5/confirmacion").exchange().expectStatus().isOk();
        reactor.test.StepVerifier.create(org.springframework.web.reactive.function.client.WebClient.create("http://localhost:" + puerto)
                        .get().uri("/pedidos/p5/eventos").retrieve().bodyToFlux(String.class).timeout(Duration.ofSeconds(5)))
                .expectNext("CONFIRMADO").verifyComplete();
    }

    void esperarSuscriptor(String id) {
        reactor.test.StepVerifier.create(Flux.interval(Duration.ofMillis(20)).filter(i -> eventos.suscriptores(id) > 0).next().timeout(Duration.ofSeconds(5))).expectNextCount(1).verifyComplete();
    }

    @Test @Order(9) void pagosCaidoEs503RapidoYConProblemDetail() {
        pagos.stop(0);
        long t0 = System.nanoTime();
        web.post().uri("/pedidos/p3/confirmacion").exchange().expectStatus().isEqualTo(503)
                .expectHeader().contentType("application/problem+json");
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 2500, "debía fallar antes del tiempo límite de pagos");
    }
}
