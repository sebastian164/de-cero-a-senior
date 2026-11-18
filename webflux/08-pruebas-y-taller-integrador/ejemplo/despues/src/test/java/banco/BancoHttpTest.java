package banco;

import static org.assertj.core.api.Assertions.assertThat;

import banco.aplicacion.VistaDeCuenta;
import banco.arranque.AplicacionBanco;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.blockhound.BlockHound;

/**
 * El banco completo: HTTP real, WebFlux, R2DBC y un PostgreSQL desechable que Testcontainers levanta y destruye.
 * Con BlockHound instalado, cualquier llamada bloqueante en un hilo del bucle de eventos rompe la prueba.
 */
@SpringBootTest(classes = AplicacionBanco.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class BancoHttpTest {

    @Container
    @ServiceConnection                                             // Spring Boot toma de aquí la URL de R2DBC
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7");

    @Autowired
    WebTestClient http;

    @BeforeAll
    static void instalarBlockHound() {
        BlockHound.builder()
                // UUID.randomUUID() lee /dev/urandom: técnicamente es E/S de archivo, en la práctica no espera nunca.
                // Se permite de forma explícita y solo ahí; cualquier otra llamada bloqueante sigue rompiendo la prueba.
                .allowBlockingCallsInside("java.util.UUID", "randomUUID")
                .install();
    }

    @Test
    void transfiereYNoRepiteLaMismaOrden() {
        String ana = abrir("Ana", 100_000);
        String luis = abrir("Luis", 0);
        String clave = UUID.randomUUID().toString();               // cada prueba con sus datos: no depende de las demás

        transferir(clave, ana, luis, 30_000).expectStatus().isCreated()
                .expectBody().jsonPath("$.resultado").isEqualTo("REALIZADA");
        transferir(clave, ana, luis, 30_000).expectStatus().isOk()
                .expectBody().jsonPath("$.resultado").isEqualTo("REPETIDA");

        assertThat(saldo(ana)).isEqualTo(70_000);
        assertThat(saldo(luis)).isEqualTo(30_000);
    }

    @Test
    void unaTransferenciaAUnaCuentaQueNoExisteNoDejaRastro() {
        String ana = abrir("Ana", 100_000);

        transferir(UUID.randomUUID().toString(), ana, "cuenta-que-no-existe", 30_000)
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("no existe la cuenta cuenta-que-no-existe");

        assertThat(saldo(ana)).isEqualTo(100_000);                 // la transacción se revirtió entera
        http.get().uri("/cuentas/{id}/movimientos", ana).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(1);   // solo el de apertura
    }

    @Test
    void sinSaldoResponde422() {
        String ana = abrir("Ana", 1_000);
        String luis = abrir("Luis", 0);

        transferir(UUID.randomUUID().toString(), ana, luis, 5_000)
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.detail").isEqualTo("saldo insuficiente en " + ana);
    }

    @Test
    void unaCuentaQueNoExisteResponde404() {
        http.get().uri("/cuentas/{id}", "cuenta-que-no-existe").exchange()
                .expectStatus().isNotFound();
    }

    // ---- ayudas ----

    private String abrir(String titular, long centavos) {
        return http.post().uri("/cuentas")
                .bodyValue(Map.of("titular", titular, "centavos", centavos))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(VistaDeCuenta.class)
                .returnResult().getResponseBody().id();
    }

    private WebTestClient.ResponseSpec transferir(String clave, String origen, String destino, long centavos) {
        return http.post().uri("/transferencias")
                .header("Idempotency-Key", clave)
                .bodyValue(Map.of("origen", origen, "destino", destino, "centavos", centavos))
                .exchange();
    }

    private long saldo(String cuentaId) {
        return http.get().uri("/cuentas/{id}", cuentaId).exchange()
                .expectStatus().isOk()
                .expectBody(VistaDeCuenta.class)
                .returnResult().getResponseBody().saldoCentavos();
    }
}
