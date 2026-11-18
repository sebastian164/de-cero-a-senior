package acortador;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Requiere PostgreSQL en BD_URL (por defecto localhost:5432/acortador). */
@SpringBootTest(properties = "acortador.clave-secreta=clave-de-las-pruebas")
@AutoConfigureMockMvc
class AcortadorIntegracionTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired ContadorDeClics contador;

    @org.junit.jupiter.api.BeforeEach
    void limpiar() {
        jdbc.sql("DELETE FROM urls").update();
    }

    String crear(String json) throws Exception {
        var r = mvc.perform(post("/v1/urls").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn();
        var m = java.util.regex.Pattern.compile("\"codigo\":\"([^\"]+)\"").matcher(r.getResponse().getContentAsString());
        assertTrue(m.find());
        return m.group(1);
    }

    @Test
    void creaYRedirige() throws Exception {
        String codigo = crear("{\"url\":\"https://example.com/una/ruta/larga?x=1\"}");
        assertEquals(7, codigo.length());
        mvc.perform(get("/" + codigo)).andExpect(status().isFound()).andExpect(header().string("Location", "https://example.com/una/ruta/larga?x=1"));
    }

    @Test
    void aliasPersonalizadoYConflicto() throws Exception {
        crear("{\"url\":\"https://example.com/a\",\"alias\":\"mi-alias\"}");
        mvc.perform(get("/mi-alias")).andExpect(status().isFound());
        mvc.perform(post("/v1/urls").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com/b\",\"alias\":\"mi-alias\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void expiracionDevuelve404() throws Exception {
        String codigo = crear("{\"url\":\"https://example.com/e\",\"expiraEn\":\"" + Instant.now().plusMillis(800) + "\"}");
        mvc.perform(get("/" + codigo)).andExpect(status().isFound());
        Thread.sleep(1000);
        mvc.perform(get("/" + codigo)).andExpect(status().isNotFound());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM urls WHERE codigo = :c").param("c", codigo).query(Long.class).single(), "eliminación perezosa");
    }

    @Test
    void validaciones() throws Exception {
        for (String malo : new String[]{"{\"url\":\"no-es-url\"}", "{\"url\":\"ftp://x.com/a\"}", "{\"url\":\"https://x.com\",\"alias\":\"a b\"}",
                "{\"url\":\"https://x.com\",\"expiraEn\":\"2000-01-01T00:00:00Z\"}"}) {
            mvc.perform(post("/v1/urls").contentType(MediaType.APPLICATION_JSON).content(malo)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/noexiste")).andExpect(status().isNotFound());
    }

    @Test
    void losClicsSeCuentanDeFormaAsincrona() throws Exception {
        String codigo = crear("{\"url\":\"https://example.com/c\"}");
        for (int i = 0; i < 25; i++) mvc.perform(get("/" + codigo)).andExpect(status().isFound());
        // inmediatamente después, la base puede no tenerlos aún: no se escribe durante la redirección
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get("/v1/urls/" + codigo + "/estadisticas")).andExpect(jsonPath("$.clics").value(25)));
    }

    /** El volcado concurrente no pierde clics: N hilos registran mientras otro hilo vuelca sin parar. */
    @Test
    void elVolcadoConcurrenteNoPierdeClics() throws Exception {
        String codigo = crear("{\"url\":\"https://example.com/concurrente\"}");
        int hilos = 8, porHilo = 50_000;
        var activo = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread volcador = new Thread(() -> { while (activo.get()) contador.volcar(); });
        volcador.start();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(hilos);
        for (int h = 0; h < hilos; h++) pool.submit(() -> { for (int i = 0; i < porHilo; i++) contador.registrar(codigo); });
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS));
        activo.set(false);
        volcador.join();
        contador.volcar();
        assertEquals((long) hilos * porHilo, jdbc.sql("SELECT clics FROM urls WHERE codigo = :c").param("c", codigo).query(Long.class).single());
    }

    @Test
    void laCacheNegativaNoImpideCrearDespuesUnCodigoQueNoExistia() throws Exception {
        mvc.perform(get("/recien-creado")).andExpect(status().isNotFound());
        mvc.perform(get("/recien-creado")).andExpect(status().isNotFound()); // segunda vez: sale de la caché negativa
        crear("{\"url\":\"https://example.com/n\",\"alias\":\"recien-creado\"}");
        mvc.perform(get("/recien-creado")).andExpect(status().isFound());
    }
}
