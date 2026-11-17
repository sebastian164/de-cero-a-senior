package reservas;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import reservas.aplicacion.ServicioReservas;
import reservas.aplicacion.ServicioReservas.Actor;
import reservas.dominio.Franja;
import reservas.dominio.Reserva;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración con Testcontainers: el PostgreSQL 18.7 de la prueba (ver {@link PostgresDePrueba}) recibe las migraciones
 * de Flyway y se demuestra que la restricción de exclusión existe y que 20 peticiones simultáneas dejan una sola reserva.
 */
class ReservasConTestcontainersTest {
    static ConfigurableApplicationContext ctx;

    @BeforeAll
    static void arrancar() throws Exception {
        String base = PostgresDePrueba.baseNueva("reservas_tc");
        ctx = new SpringApplicationBuilder(AplicacionReservas.class)
                .properties("RESERVAS_DB_URL=" + base, "RESERVAS_DB_USUARIO=reservas", "RESERVAS_DB_CLAVE=reservas",
                        "PUERTO=" + PostgresDePrueba.puertoLibre(), Tokens.propiedadClavePublica()).run();
    }

    @AfterAll
    static void parar() {
        if (ctx != null) ctx.close();
    }

    @Test
    void el_contenedor_es_el_de_la_prueba_y_las_migraciones_crearon_la_restriccion_de_exclusion() {
        assertThat(PostgresDePrueba.POSTGRES.isRunning()).isTrue();
        JdbcClient jdbc = ctx.getBean(JdbcClient.class);
        assertThat(jdbc.sql("SHOW server_version").query(String.class).single()).startsWith("18.7");
        assertThat(jdbc.sql("SELECT count(*) FROM pg_constraint WHERE conname = 'sin_solape' AND contype = 'x'").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void con_veinte_peticiones_simultaneas_contra_el_postgresql_del_contenedor_solo_una_gana() throws Exception {
        ServicioReservas servicio = ctx.getBean(ServicioReservas.class);
        Instant ini = Instant.now().plus(72, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Franja f = new Franja(ini, ini.plusSeconds(3600));
        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch salida = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger(), ocupadas = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String quien = "u" + i;
            fs.add(pool.submit(() -> {
                salida.await();
                try { servicio.reservar(new Actor(quien, false), null, "cancha-1", f); exitos.incrementAndGet(); }
                catch (Reserva.FranjaOcupada e) { ocupadas.incrementAndGet(); }
                return null;
            }));
        }
        salida.countDown();
        for (Future<?> x : fs) x.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(exitos.get()).isEqualTo(1);
        assertThat(ocupadas.get()).isEqualTo(n - 1);
    }
}
