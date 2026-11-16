package sincrona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import inventario.AplicacionInventario;
import inventario.Comportamiento;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import soporte.Entorno;
import tienda.AplicacionTienda;

/** Dos servicios Spring Boot reales en puertos 43081 (tienda) y 43082 (inventario), y PostgreSQL real. */
class ComunicacionSincronaTest {

    static ConfigurableApplicationContext inventario;
    static ConfigurableApplicationContext tienda;
    static Comportamiento comportamiento;
    static final HttpClient http = HttpClient.newHttpClient();
    static final StringBuilder informe = new StringBuilder();

    @BeforeAll
    static void arrancar() throws Exception {
        try (Connection c = Entorno.conectar(Entorno.baseNueva("n9_02"))) {
            c.createStatement().execute("""
                CREATE TABLE cobros (id BIGSERIAL PRIMARY KEY, pedido_id BIGINT NOT NULL, monto NUMERIC(12,2) NOT NULL);
                CREATE TABLE claves_idempotencia (clave VARCHAR(64) PRIMARY KEY, pedido_id BIGINT NOT NULL, cobro_id BIGINT);
                """);
        }
        inventario = new SpringApplicationBuilder(AplicacionInventario.class).properties("spring.config.name=inventario").run();
        tienda = new SpringApplicationBuilder(AplicacionTienda.class).properties("spring.config.name=tienda").run();
        comportamiento = inventario.getBean(Comportamiento.class);
    }

    @AfterAll
    static void parar() {
        System.out.println(informe);
        if (comportamiento != null && comportamiento.colgado != null) {
            comportamiento.colgado.countDown();
        }
        if (tienda != null) {
            tienda.close();
        }
        if (inventario != null) {
            inventario.close();
        }
    }

    @BeforeEach
    void reiniciar() {
        comportamiento.reiniciar();
    }

    static void anotar(String formato, Object... args) {
        informe.append(String.format(Locale.ROOT, formato, args)).append('\n');
    }

    static HttpResponse<String> llamar(String metodo, String ruta, Duration espera) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create("http://localhost:43081" + ruta)).timeout(espera)
                .method(metodo, HttpRequest.BodyPublishers.noBody()).build();
        return http.send(r, HttpResponse.BodyHandlers.ofString());
    }

    static List<CompletableFuture<HttpResponse<String>>> rafaga(String ruta, int n) {
        List<CompletableFuture<HttpResponse<String>>> f = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            f.add(http.sendAsync(HttpRequest.newBuilder(URI.create("http://localhost:43081" + ruta)).timeout(Duration.ofSeconds(40)).build(),
                    HttpResponse.BodyHandlers.ofString()));
        }
        return f;
    }

    @Test
    void paso1_sin_tiempo_limite_un_inventario_colgado_agota_los_hilos_de_la_tienda() throws Exception {
        comportamiento.colgado = new CountDownLatch(1);
        var colgadas = rafaga("/productos-sin-limite/1", 15);          // 15 peticiones para 10 hilos de Tomcat
        Thread.sleep(1000);
        assertThatThrownBy(() -> llamar("GET", "/salud", Duration.ofSeconds(2)))
                .as("/salud no usa inventario y aun así no responde").isInstanceOf(HttpTimeoutException.class);
        anotar("Paso 1: sin tiempo límite, con inventario colgado y 15 peticiones, /salud no respondió en 2 s (10 hilos ocupados esperando)");
        comportamiento.colgado.countDown();                          // inventario se recupera
        for (var f : colgadas) {
            assertThat(f.get().statusCode()).isEqualTo(200);
        }
        assertThat(llamar("GET", "/salud", Duration.ofSeconds(2)).body()).isEqualTo("ok");
    }

    @Test
    void paso2_con_tiempo_limite_los_hilos_se_liberan_y_salud_responde() throws Exception {
        comportamiento.colgado = new CountDownLatch(1);
        var colgadas = rafaga("/productos/1", 15);
        Thread.sleep(300);
        long t0 = System.nanoTime();
        assertThat(llamar("GET", "/salud", Duration.ofSeconds(5)).body()).isEqualTo("ok");
        long ms = (System.nanoTime() - t0) / 1_000_000;
        for (var f : colgadas) {
            assertThat(f.get().statusCode()).isEqualTo(504);
        }
        comportamiento.colgado.countDown();
        anotar("Paso 2: con tiempo límite de 300 ms y 3 intentos, /salud respondió en %d ms; las 15 peticiones devolvieron 504; "
                + "inventario recibió %d llamadas (15 × 3)", ms, comportamiento.llamadasExistencias.get());
        assertThat(ms).isLessThan(5000);
        assertThat(comportamiento.llamadasExistencias.get()).isEqualTo(45);
    }

    @Test
    void paso2_reintentos_con_retroceso_exponencial_y_variacion() throws Exception {
        comportamiento.colgado = new CountDownLatch(1);
        assertThat(llamar("GET", "/productos/1", Duration.ofSeconds(5)).statusCode()).isEqualTo(504);
        comportamiento.colgado.countDown();
        List<Long> t = comportamiento.llegadasNanos;
        assertThat(t).hasSize(3);
        // Entre llegadas: 300 ms de tiempo límite + espera. Espera 1 ≈ 100 ms ± 50 %, espera 2 ≈ 200 ms ± 50 %.
        long e1 = (t.get(1) - t.get(0)) / 1_000_000 - 300;
        long e2 = (t.get(2) - t.get(1)) / 1_000_000 - 300;
        anotar("Paso 2: esperas entre intentos medidas: %d ms y %d ms (base 100 y 200 ms con variación de ±50 %%)", e1, e2);
        assertThat(e1).isBetween(40L, 200L);
        assertThat(e2).isBetween(90L, 350L);
    }

    @Test
    void paso2_con_un_inventario_lento_al_azar_el_reintento_sube_la_tasa_de_exito() throws Exception {
        comportamiento.probabilidadLento = 0.4;
        int sinReintento = exitos("/productos-sin-reintento/1", 100);
        int conReintento = exitos("/productos/1", 100);
        anotar("Paso 2: inventario lento al azar (40 %% de las peticiones tardan 1 s). Éxitos de 100: sin reintento %d, con 3 intentos %d "
                + "(esperado 60 y 93,6)", sinReintento, conReintento);
        assertThat(sinReintento).isBetween(40, 80);
        assertThat(conReintento).isGreaterThan(85);
    }

    @Test
    void paso2_los_errores_4xx_no_se_reintentan() throws Exception {
        assertThat(llamar("GET", "/productos/404", Duration.ofSeconds(5)).statusCode()).isEqualTo(404);
        assertThat(comportamiento.llamadasExistencias.get()).isEqualTo(1);
    }

    int exitos(String ruta, int n) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Integer>> f = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            f.add(pool.submit(() -> llamar("GET", ruta, Duration.ofSeconds(10)).statusCode()));
        }
        int ok = 0;
        for (var x : f) {
            ok += x.get() == 200 ? 1 : 0;
        }
        pool.shutdown();
        return ok;
    }

    static int cobrosDe(long pedido) throws Exception {
        String r = http.send(HttpRequest.newBuilder(URI.create("http://localhost:43082/cobros/cuenta/" + pedido)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        return Integer.parseInt(r.replaceAll("\\D", ""));
    }

    @Test
    void paso3_la_respuesta_se_pierde_y_el_reintento_cobra_tres_veces() throws Exception {
        comportamiento.perderRespuestaMs = 1000;
        var r = llamar("POST", "/pagos/42", Duration.ofSeconds(10));
        Thread.sleep(1200);
        anotar("Paso 3: POST /pagos/42 con la respuesta perdida: la tienda respondió %d y inventario registró %d cobros del pedido 42",
                r.statusCode(), cobrosDe(42));
        assertThat(r.statusCode()).isEqualTo(504);
        assertThat(cobrosDe(42)).isEqualTo(3);
    }

    @Test
    void paso4_con_clave_de_idempotencia_el_reintento_no_duplica() throws Exception {
        comportamiento.perderRespuestaMs = 1000;
        var r = llamar("POST", "/pagos-idempotentes/43", Duration.ofSeconds(10));
        Thread.sleep(1200);
        anotar("Paso 4: POST /pagos-idempotentes/43 con la respuesta perdida: la tienda respondió %d (los tres intentos vencieron), "
                + "inventario registró %d cobro", r.statusCode(), cobrosDe(43));
        assertThat(cobrosDe(43)).isEqualTo(1);
        // Cuando la red se recupera, el mismo reintento (misma clave) obtiene el resultado guardado.
        comportamiento.perderRespuestaMs = 0;
        String clave = "clave-pedido-44";
        String primera = cobrarDirecto(clave, 44);
        String segunda = cobrarDirecto(clave, 44);
        assertThat(segunda).isEqualTo(primera);
        assertThat(cobrosDe(44)).isEqualTo(1);
    }

    static String cobrarDirecto(String clave, long pedido) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:43082/cobros-idempotentes"))
                .header("Idempotency-Key", clave).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"pedidoId\":" + pedido + ",\"monto\":100}")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    @Test
    void paso4_dos_peticiones_simultaneas_con_la_misma_clave_cobran_una_vez() throws Exception {
        for (long pedido = 100; pedido < 120; pedido++) {
            long p = pedido;
            CountDownLatch salida = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            Future<String> a = pool.submit(() -> { salida.await(); return cobrarDirecto("simultanea-" + p, p); });
            Future<String> b = pool.submit(() -> { salida.await(); return cobrarDirecto("simultanea-" + p, p); });
            salida.countDown();
            assertThat(a.get()).isEqualTo(b.get());
            assertThat(cobrosDe(p)).isEqualTo(1);
            pool.shutdown();
        }
        anotar("Paso 4: 20 pares de peticiones simultáneas con la misma clave: 1 cobro por pedido y la misma respuesta en ambas");
    }
}
