package resiliencia;

import static org.assertj.core.api.Assertions.assertThat;

import dependencia.AplicacionDependencia;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import servicio.FiltroLimiteDeTasa;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import servicio.AplicacionServicio;

/** Taller n9-05: tres dependencias reales (puertos 43093-43095) y un servicio Spring Boot con Resilience4j (43091). */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResilienciaTest {

    static ConfigurableApplicationContext critico, recomendaciones, resenas, servicio;
    static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static CircuitBreakerRegistry cbs;

    static ConfigurableApplicationContext dependencia(int puerto) {
        return new SpringApplicationBuilder(AplicacionDependencia.class)
                .properties("spring.config.name=dependencia", "server.port=" + puerto).run();
    }

    static AplicacionDependencia.Control control(ConfigurableApplicationContext c) {
        return c.getBean(AplicacionDependencia.Control.class);
    }

    @BeforeAll
    static void arrancar() {
        critico = dependencia(43093);
        recomendaciones = dependencia(43094);
        resenas = dependencia(43095);
        servicio = new SpringApplicationBuilder(AplicacionServicio.class).properties("spring.config.name=servicio", "limite.max-clientes=300").run();
        cbs = servicio.getBean(CircuitBreakerRegistry.class);
    }

    @AfterAll
    static void parar() {
        for (var c : new ConfigurableApplicationContext[]{servicio, critico, recomendaciones, resenas}) {
            if (c != null) c.close();
        }
    }

    @BeforeEach
    void reiniciar() {
        for (var c : new ConfigurableApplicationContext[]{critico, recomendaciones, resenas}) {
            if (c != null && c.isActive()) {
                control(c).falla = false;
                control(c).retrasoMs = 0;
            }
        }
        cbs.circuitBreaker("recomendaciones").reset();
        cbs.circuitBreaker("resenas").reset();
    }

    record Resp(int estado, String cuerpo, long ms, String retryAfter) { }

    static Resp get(String ruta, String cliente, Duration plazo) {
        long t0 = System.nanoTime();
        try {
            var b = HttpRequest.newBuilder(URI.create("http://localhost:43091" + ruta)).timeout(plazo);
            if (cliente != null) b.header("X-Cliente", cliente);
            var r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Resp(r.statusCode(), r.body(), (System.nanoTime() - t0) / 1_000_000, r.headers().firstValue("Retry-After").orElse(null));
        } catch (Exception e) {
            return new Resp(-1, e.getClass().getSimpleName(), (System.nanoTime() - t0) / 1_000_000, null);
        }
    }

    static Resp get(String ruta) { return get(ruta, null, Duration.ofSeconds(10)); }

    // ---------- 1. Clasificación y comportamiento ante la caída de cada dependencia ----------
    @Test @Order(1)
    void paso1_la_critica_caida_da_503_y_las_opcionales_caidas_degradan_la_pagina_sin_error() {
        assertThat(get("/pagina/1").cuerpo()).contains("recomendado-para-1").contains("reseña-de-1");

        control(recomendaciones).falla = true;
        Resp r = get("/pagina/1");
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo()).contains("más-vendido-1").contains("\"degradado\":true");

        control(recomendaciones).falla = false;
        control(resenas).falla = true;
        r = get("/pagina/1");
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo()).contains("\"disponibles\":false").contains("recomendado-para-1");

        control(resenas).falla = false;
        control(critico).falla = true;
        r = get("/pagina/1");
        assertThat(r.estado()).isEqualTo(503);
        assertThat(r.cuerpo()).contains("SERVICIO_NO_DISPONIBLE");
    }

    // ---------- 2. Cortacircuitos, tiempo límite y transiciones ----------
    @Test @Order(2)
    void paso2_el_cortacircuitos_se_abre_deja_de_llamar_pasa_a_semiabierto_y_se_cierra_y_las_metricas_lo_reflejan() throws Exception {
        CircuitBreaker cb = cbs.circuitBreaker("recomendaciones");
        List<String> transiciones = new CopyOnWriteArrayList<>();
        cb.getEventPublisher().onStateTransition(e -> transiciones.add(e.getStateTransition().getFromState() + "->" + e.getStateTransition().getToState()));
        MeterRegistry metricas = servicio.getBean(MeterRegistry.class);
        var control = control(recomendaciones);

        // Tiempo límite del cliente (400 ms): una dependencia que tarda 2 s cuenta como fallo y no retiene 2 s.
        control.retrasoMs = 2000;
        Resp lenta = get("/pagina/1");
        assertThat(lenta.estado()).isEqualTo(200);
        assertThat(lenta.cuerpo()).contains("\"degradado\":true");
        assertThat(lenta.ms()).as("tiempo límite de 400 ms, no 2 s").isLessThan(1200);

        control.retrasoMs = 0;
        control.falla = true;
        for (int i = 0; i < 10; i++) {
            get("/pagina/1");
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(metricas.get("resilience4j.circuitbreaker.state").tag("name", "recomendaciones").tag("state", "open").gauge().value()).isEqualTo(1.0);
        String vistaActuator = get("/actuator/circuitbreakers").cuerpo();
        assertThat(vistaActuator).contains("recomendaciones").contains("OPEN");

        int llamadasConElCircuitoAbierto = control.llamadas.get();
        for (int i = 0; i < 20; i++) {
            Resp r = get("/pagina/1");
            assertThat(r.cuerpo()).contains("\"degradado\":true");
        }
        assertThat(control.llamadas.get()).as("abierto: no se llama a la dependencia").isEqualTo(llamadasConElCircuitoAbierto);

        control.falla = false;                                   // la dependencia se recupera
        Thread.sleep(2300);                                      // wait-duration-in-open-state: 2s
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(metricas.get("resilience4j.circuitbreaker.state").tag("name", "recomendaciones").tag("state", "half_open").gauge().value()).isEqualTo(1.0);
        get("/pagina/1");
        get("/pagina/1");                                        // 2 llamadas de prueba exitosas
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(transiciones).contains("CLOSED->OPEN", "OPEN->HALF_OPEN", "HALF_OPEN->CLOSED");
        System.out.println("Transiciones observadas: " + transiciones);
    }

    // ---------- 3. Sin mamparo, una opcional lenta deja sin servicio una ruta que no la usa ----------
    @Test @Order(3)
    void paso3_sin_mamparo_la_ruta_precio_deja_de_responder_y_con_mamparo_sigue_respondiendo() throws Exception {
        control(recomendaciones).retrasoMs = 3000;
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            // ANTES: 15 peticiones a la ruta sin protección ocupan los 10 hilos de Tomcat.
            List<CompletableFuture<Resp>> carga = new ArrayList<>();
            for (int i = 0; i < 15; i++) carga.add(CompletableFuture.supplyAsync(() -> get("/pagina-sin-mamparo/1"), pool));
            Thread.sleep(500);
            Resp precio = get("/precio/1", null, Duration.ofSeconds(1));
            System.out.println("Sin mamparo: /precio/1 -> " + precio);
            assertThat(precio.estado()).as("/precio no usa recomendaciones, pero no responde").isEqualTo(-1);
            carga.forEach(CompletableFuture::join);
            assertThat(get("/precio/1").estado()).as("al terminar la carga se recupera").isEqualTo(200);

            // DESPUÉS: la ruta protegida (mamparo de 3 + límite de 400 ms + cortacircuitos).
            carga.clear();
            for (int i = 0; i < 15; i++) carga.add(CompletableFuture.supplyAsync(() -> get("/pagina/1"), pool));
            Thread.sleep(150);
            Resp protegido = get("/precio/1", null, Duration.ofSeconds(1));
            System.out.println("Con mamparo: /precio/1 -> " + protegido);
            assertThat(protegido.estado()).isEqualTo(200);
            assertThat(protegido.ms()).isLessThan(500);
            List<Resp> paginas = carga.stream().map(CompletableFuture::join).toList();
            assertThat(paginas).allMatch(r -> r.estado() == 200);
            assertThat(paginas).as("las que no entraron al mamparo recibieron el respaldo al instante")
                    .anyMatch(r -> r.cuerpo().contains("\"degradado\":true") && r.ms() < 300);
        }
    }

    // ---------- 4. Respaldo adecuado y respaldo engañoso ----------
    @Test @Order(4)
    void paso4_el_respaldo_enganoso_miente_con_saldo_cero_y_el_adecuado_admite_que_no_sabe() {
        assertThat(get("/saldo-enganoso/9").cuerpo()).contains("250000");
        assertThat(get("/saldo/9").cuerpo()).contains("250000");
        control(critico).falla = true;
        Resp enganoso = get("/saldo-enganoso/9");
        assertThat(enganoso.estado()).isEqualTo(200);
        assertThat(enganoso.cuerpo()).as("200 con saldo 0: una mentira").contains("\"saldo\":0");
        Resp adecuado = get("/saldo/9");
        assertThat(adecuado.estado()).as("no inventa el saldo").isEqualTo(503);
        // Contraste con un respaldo adecuado de una dependencia opcional (paso 1): contenido razonable y marcado "degradado".
    }

    // ---------- 5. Límite de tasa por cliente ----------
    @Test @Order(5)
    void paso5_el_sexto_envio_del_mismo_cliente_recibe_429_con_retry_after_y_otro_cliente_no_se_ve_afectado() throws Exception {
        List<Resp> a = new ArrayList<>();
        for (int i = 0; i < 8; i++) a.add(get("/limitado/ping", "cliente-a", Duration.ofSeconds(3)));
        assertThat(a.subList(0, 5)).extracting(Resp::estado).containsOnly(200);     // pasan el filtro y llegan a la ruta real
        assertThat(a.subList(5, 8)).extracting(Resp::estado).containsOnly(429);
        assertThat(a.get(5).retryAfter()).isEqualTo("2");
        assertThat(get("/limitado/ping", "cliente-b", Duration.ofSeconds(3)).estado()).isEqualTo(200);
        Thread.sleep(2100);
        assertThat(get("/limitado/ping", "cliente-a", Duration.ofSeconds(3)).estado()).isEqualTo(200);
    }

    @Test @Order(5)
    void paso5b_el_registro_de_limitadores_tiene_tope_aunque_el_cliente_invente_cabeceras() {
        var filtro = servicio.getBean(FiltroLimiteDeTasa.class);
        for (int i = 0; i < filtro.maximo() + 200; i++) get("/limitado/ping", "inventado-" + i, Duration.ofSeconds(3));
        assertThat(filtro.maximo()).isEqualTo(300);
        assertThat(filtro.clientesRegistrados()).isEqualTo(300);          // 500 clientes inventados, el registro se quedó en el tope
    }

    // ---------- 5c. Regresión: respaldos concurrentes (Resilience4j 2.4.0 falla con respaldos no públicos) ----------
    @Test @Order(5)
    void paso5c_doscientos_respaldos_concurrentes_no_producen_ningun_500() throws Exception {
        control(recomendaciones).falla = true;
        control(resenas).falla = true;
        int errores = 0;
        for (int ronda = 0; ronda < 5; ronda++) {
            cbs.circuitBreaker("recomendaciones").reset();
            cbs.circuitBreaker("resenas").reset();
            List<java.util.concurrent.Future<Resp>> fs = new ArrayList<>();
            var listos = new java.util.concurrent.CountDownLatch(200);
            var salida = new java.util.concurrent.CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(200)) {
                for (int i = 0; i < 200; i++) fs.add(pool.submit(() -> { listos.countDown(); salida.await(); return get("/pagina/1"); }));
                listos.await(); salida.countDown();
                for (var f : fs) { Resp r = f.get(); if (r.estado() != 200) errores++; }
            }
        }
        assertThat(errores).as("respuestas distintas de 200 en 1000 peticiones con las dos dependencias opcionales caídas").isZero();
    }

    // ---------- 6. Experimento de caos ----------
    @Test @Order(6)
    void paso6_caos_se_apaga_una_dependencia_opcional_y_luego_la_critica_en_plena_carga() throws Exception {
        final int segundos = 14, rps = 40;
        record Muestra(long segundo, int estado, boolean degradada, long ms) { }
        List<Muestra> muestras = Collections.synchronizedList(new ArrayList<>());
        long inicio = System.nanoTime();
        var programador = Executors.newScheduledThreadPool(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            programador.scheduleAtFixedRate(() -> pool.submit(() -> {
                long seg = (System.nanoTime() - inicio) / 1_000_000_000L;
                Resp r = get("/pagina/1", null, Duration.ofSeconds(5));
                muestras.add(new Muestra(seg, r.estado(), r.cuerpo().contains("\"degradado\":true"), r.ms()));
            }), 0, 1000 / rps, TimeUnit.MILLISECONDS);

            Thread.sleep(3000);
            recomendaciones.close();                              // t=3 s: se apaga recomendaciones (opcional)
            Thread.sleep(5000);
            recomendaciones = dependencia(43094);                 // t=8 s: vuelve
            Thread.sleep(3000);
            critico.close();                                      // t=11 s: se apaga el catálogo (crítico)
            Thread.sleep(3000);
            programador.shutdownNow();
        }
        critico = dependencia(43093);

        var porSegundo = new TreeMap<Long, List<Muestra>>();
        synchronized (muestras) { muestras.forEach(m -> porSegundo.computeIfAbsent(m.segundo(), k -> new ArrayList<>()).add(m)); }
        StringBuilder md = new StringBuilder("| Segundo | Peticiones | 200 completas | 200 degradadas | 503 | Otros | p95 ms |\n|---:|---:|---:|---:|---:|---:|---:|\n");
        porSegundo.forEach((s, l) -> {
            long comp = l.stream().filter(m -> m.estado() == 200 && !m.degradada()).count();
            long deg = l.stream().filter(m -> m.estado() == 200 && m.degradada()).count();
            long e503 = l.stream().filter(m -> m.estado() == 503).count();
            long otros = l.size() - comp - deg - e503;
            var ms = l.stream().mapToLong(Muestra::ms).sorted().toArray();
            md.append(String.format(Locale.ROOT, "| %d | %d | %d | %d | %d | %d | %d |%n", s, l.size(), comp, deg, e503, otros, ms[(int) Math.floor(ms.length * 0.95) - (ms.length > 1 ? 1 : 0)]));
        });
        Files.writeString(Path.of("target/caos.md"), md.toString());
        System.out.println(md);

        // Mientras estuvo apagada la dependencia opcional (segundos 4 a 7): sin un solo error para el usuario.
        var fase1 = muestras.stream().filter(m -> m.segundo() >= 4 && m.segundo() <= 7).toList();
        assertThat(fase1).isNotEmpty().allMatch(m -> m.estado() == 200);
        assertThat(fase1.stream().filter(Muestra::degradada).count()).isGreaterThan(fase1.size() * 8 / 10);
        // Con el catálogo (crítico) apagado (segundos 12 a 13): todos reciben 503 limpio y rápido.
        var fase2 = muestras.stream().filter(m -> m.segundo() >= 12).toList();
        assertThat(fase2).isNotEmpty().allMatch(m -> m.estado() == 503);
        assertThat(fase2.stream().mapToLong(Muestra::ms).max().orElse(0)).isLessThan(1500);
    }
}
