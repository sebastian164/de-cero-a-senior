package reservas;

import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import reservas.aplicacion.ServicioReservas;
import reservas.aplicacion.ServicioReservas.Actor;
import reservas.dominio.Franja;
import reservas.dominio.Reserva;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** PostgreSQL real (Testcontainers) y la aplicación completa en un puerto libre. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReservasTest {
    static ConfigurableApplicationContext ctx;
    static final HttpClient http = HttpClient.newHttpClient();
    static String URL;

    static String propiedad() {
        try { return Tokens.propiedadClavePublica(); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @BeforeAll
    static void arrancar() {
        String base = PostgresDePrueba.baseNueva("reservas_http");
        int puerto = PostgresDePrueba.puertoLibre();
        URL = "http://localhost:" + puerto;
        ctx = new SpringApplicationBuilder(AplicacionReservas.class)
                .properties("RESERVAS_DB_URL=" + base, "RESERVAS_DB_USUARIO=reservas", "RESERVAS_DB_CLAVE=reservas", "PUERTO=" + puerto, propiedad()).run();
    }

    @AfterAll
    static void parar() { if (ctx != null) ctx.close(); }

    static Instant en(long horas) { return Instant.now().plus(horas, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS); }

    static HttpResponse<String> llamar(String metodo, String ruta, String usuario, String rol, String clave, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(URL + ruta)).header("Content-Type", "application/json");
        if (usuario != null) b.header("Authorization", "Bearer " + Tokens.token(usuario, rol));
        if (clave != null) b.header("Idempotency-Key", clave);
        b.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String json(String espacio, Instant ini, Instant fin) {
        return "{\"espacio\":\"%s\",\"inicio\":\"%s\",\"fin\":\"%s\"}".formatted(espacio, ini, fin);
    }

    static String id(HttpResponse<String> r) {
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f-]+)\"").matcher(r.body());
        assertThat(m.find()).as(r.body()).isTrue();
        return m.group(1);
    }

    @Test @Order(1)
    void con_veinte_peticiones_simultaneas_solo_una_reserva_gana() throws Exception {
        ServicioReservas servicio = ctx.getBean(ServicioReservas.class);
        Franja f = new Franja(en(72), en(73));
        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch salida = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger(), ocupadas = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String quien = "u" + i;
            fs.add(pool.submit(() -> {
                salida.await();
                try { servicio.reservar(new Actor(quien, false), null, "sala-1", f); exitos.incrementAndGet(); }
                catch (Reserva.FranjaOcupada e) { ocupadas.incrementAndGet(); }
                return null;
            }));
        }
        salida.countDown();
        for (Future<?> x : fs) x.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(exitos.get()).isEqualTo(1);
        assertThat(ocupadas.get()).isEqualTo(n - 1);
        assertThat(ctx.getBean(JdbcClient.class).sql("SELECT count(*) FROM reservas WHERE espacio='sala-1' AND estado='ACTIVA'").query(Long.class).single()).isEqualTo(1L);
    }

    @Test @Order(2)
    void http_crear_idempotencia_errores_uniformes_y_openapi() throws Exception {
        Instant base = en(100);
        String cuerpo = json("sala-2", base, base.plusSeconds(3600));
        var a = llamar("POST", "/reservas", "ana", null, "clave-1", cuerpo);
        assertThat(a.statusCode()).isEqualTo(201);
        var repetida = llamar("POST", "/reservas", "ana", null, "clave-1", cuerpo);
        assertThat(repetida.statusCode()).isEqualTo(201);
        assertThat(id(repetida)).isEqualTo(id(a));
        assertThat(ctx.getBean(JdbcClient.class).sql("SELECT count(*) FROM reservas WHERE espacio='sala-2'").query(Long.class).single()).isEqualTo(1L);

        var otra = llamar("POST", "/reservas", "beto", null, null, json("sala-2", base, base.plusSeconds(7200)));
        assertThat(otra.statusCode()).isEqualTo(409);
        assertThat(otra.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");

        assertThat(llamar("POST", "/reservas", "ana", null, null, "{\"espacio\":\"\"}").statusCode()).isEqualTo(400);
        assertThat(llamar("GET", "/reservas", null, null, null, null).statusCode()).isEqualTo(401);

        var docs = http.send(HttpRequest.newBuilder(URI.create(URL + "/v3/api-docs")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body()).contains("\"/reservas\"").contains("Idempotency-Key");
    }

    @Test @Order(3)
    void cada_usuario_ve_lo_suyo_el_admin_todo_y_la_lista_se_pagina() throws Exception {
        for (int i = 0; i < 5; i++) assertThat(llamar("POST", "/reservas", "carla", null, null, json("sala-3", en(200 + 2 * i), en(201 + 2 * i))).statusCode()).isEqualTo(201);
        var deDaniel = llamar("POST", "/reservas", "daniel", null, null, json("sala-4", en(300), en(301)));
        String idDaniel = id(deDaniel);

        assertThat(llamar("GET", "/reservas/" + idDaniel, "carla", null, null, null).statusCode()).isEqualTo(403);
        assertThat(llamar("DELETE", "/reservas/" + idDaniel, "carla", null, null, null).statusCode()).isEqualTo(403);
        assertThat(llamar("GET", "/reservas/" + idDaniel, "admin", "ADMIN", null, null).statusCode()).isEqualTo(200);
        assertThat(llamar("GET", "/reservas/" + UUID.randomUUID(), "carla", null, null, null).statusCode()).isEqualTo(404);

        var pagina = llamar("GET", "/reservas?pagina=1&tamano=2", "carla", null, null, null);
        assertThat(pagina.body()).contains("\"total\":5").contains("\"pagina\":1").contains("\"tamano\":2");
        assertThat(pagina.body().split("\"espacio\"").length - 1).isEqualTo(2);
        assertThat(pagina.body()).doesNotContain("daniel");
        assertThat(llamar("GET", "/reservas?tamano=1000", "carla", null, null, null).statusCode()).isEqualTo(400);
        assertThat(llamar("GET", "/reservas?tamano=100", "admin", "ADMIN", null, null).body()).contains("daniel").contains("carla");
    }

    @Test @Order(4)
    void cancelar_respeta_el_plazo_y_pasa_la_franja_a_la_lista_de_espera() throws Exception {
        var tarde = llamar("POST", "/reservas", "eva", null, null, json("sala-5", en(2), en(3)));
        var rechazo = llamar("DELETE", "/reservas/" + id(tarde), "eva", null, null, null);
        assertThat(rechazo.statusCode()).isEqualTo(422);

        Instant ini = en(100), fin = en(102); // se calculan una vez: la espera se empareja por franja exacta
        var buena = llamar("POST", "/reservas", "eva", null, null, json("sala-6", ini, fin));
        assertThat(llamar("POST", "/reservas", "fer", null, null, json("sala-6", ini, fin)).statusCode()).isEqualTo(409);
        assertThat(llamar("POST", "/reservas/espera", "fer", null, null, json("sala-6", ini, fin)).statusCode()).isEqualTo(202);
        assertThat(llamar("DELETE", "/reservas/" + id(buena), "eva", null, null, null).statusCode()).isEqualTo(204);
        var deFer = llamar("GET", "/reservas?tamano=100", "fer", null, null, null);
        assertThat(deFer.body()).contains("\"usuario\":\"fer\"").contains("sala-6").contains("ACTIVA");
        // la franja ya no se puede reservar de nuevo: la tiene Fer
        assertThat(llamar("POST", "/reservas", "gus", null, null, json("sala-6", ini, fin)).statusCode()).isEqualTo(409);
    }

    @Test @Order(5)
    void seguridad_solo_entra_quien_trae_un_token_firmado_y_vigente_y_las_cabeceras_falsificadas_no_sirven() throws Exception {
        var sinToken = llamar("GET", "/reservas", null, null, null, null);
        assertThat(sinToken.statusCode()).isEqualTo(401);
        assertThat(sinToken.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");

        // Las cabeceras de la versión anterior (X-Usuario / X-Rol) ya no identifican a nadie.
        var falsa = http.send(HttpRequest.newBuilder(URI.create(URL + "/reservas")).header("X-Usuario", "admin").header("X-Rol", "ADMIN").build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(falsa.statusCode()).isEqualTo(401);

        assertThat(conToken("GET", "/reservas", Tokens.tokenDeOtraClave("admin", "ADMIN")).statusCode()).as("firmado con otra clave").isEqualTo(401);
        assertThat(conToken("GET", "/reservas", Tokens.tokenVencido("ana")).statusCode()).as("vencido").isEqualTo(401);
        assertThat(conToken("GET", "/reservas", "esto.no.es-un-jwt").statusCode()).as("basura").isEqualTo(401);
        assertThat(conToken("GET", "/reservas", Tokens.token("ana", null)).statusCode()).isEqualTo(200);

        // Un usuario corriente no se vuelve administrador diciéndolo en una cabecera: el rol solo viene del token.
        var intento = http.send(HttpRequest.newBuilder(URI.create(URL + "/reservas?tamano=100")).header("Authorization", "Bearer " + Tokens.token("ana", null))
                .header("X-Rol", "ADMIN").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(intento.body()).doesNotContain("daniel");
        assertThat(llamar("GET", "/reservas?tamano=100", "admin", "ADMIN", null, null).body()).contains("daniel");
    }

    static HttpResponse<String> conToken(String metodo, String ruta, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(URL + ruta)).header("Authorization", "Bearer " + token)
                .method(metodo, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test @Order(6)
    void disponibilidad_por_franjas_muestra_lo_ocupado_y_los_huecos_libres() throws Exception {
        Instant base = en(400);
        assertThat(llamar("POST", "/reservas", "hugo", null, null, json("sala-7", base.plusSeconds(3600), base.plusSeconds(7200))).statusCode()).isEqualTo(201);
        assertThat(llamar("POST", "/reservas", "hugo", null, null, json("sala-7", base.plusSeconds(10800), base.plusSeconds(14400))).statusCode()).isEqualTo(201);
        var r = llamar("GET", "/reservas/disponibilidad?espacio=sala-7&desde=" + base + "&hasta=" + base.plusSeconds(18000), "ana", null, null, null);
        assertThat(r.statusCode()).isEqualTo(200);
        // libres: [0h,1h), [2h,3h), [4h,5h)
        assertThat(r.body().split("\"libres\":")[1]).contains(base.toString()).contains(base.plusSeconds(3600).toString())
                .contains(base.plusSeconds(7200).toString()).contains(base.plusSeconds(10800).toString())
                .contains(base.plusSeconds(14400).toString()).contains(base.plusSeconds(18000).toString());
        assertThat(r.body().split("\"libres\":")[1].split("\"inicio\"").length - 1).isEqualTo(3);
        assertThat(r.body().split("\"libres\":")[0].split("\"inicio\"").length - 1).as("dos franjas ocupadas").isEqualTo(2);
        assertThat(llamar("GET", "/reservas/disponibilidad?espacio=sala-7&desde=" + base + "&hasta=" + base, "ana", null, null, null).statusCode()).isEqualTo(400);
    }
}
