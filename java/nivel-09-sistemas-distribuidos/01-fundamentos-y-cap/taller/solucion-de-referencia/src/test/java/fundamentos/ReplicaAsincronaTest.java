package fundamentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import soporte.Entorno;

/** Paso 3: PostgreSQL con una réplica asíncrona (infra/compose.yml, perfil replica; retraso de aplicación de 2 s). */
class ReplicaAsincronaTest {

    static Perfiles perfiles;

    @BeforeAll
    static void preparar() throws Exception {
        try (Connection c = DriverManager.getConnection(Entorno.PG_PRIMARIO, "curso", "curso")) {
            c.createStatement().execute("DROP TABLE IF EXISTS perfiles; CREATE TABLE perfiles (usuario BIGINT PRIMARY KEY, nombre TEXT);"
                    + "INSERT INTO perfiles VALUES (1, 'Ana'), (2, 'Beto'), (3, 'Carla')");
        }
        perfiles = new Perfiles(Entorno.PG_PRIMARIO, Entorno.PG_REPLICA, Clock.systemUTC());
        for (int i = 0; i < 200 && !tablaYFilasEnLaReplica(); i++) {
            Thread.sleep(100);     // la tabla y los datos llegan a la réplica (con su retraso de aplicación)
        }
        try (Connection r = DriverManager.getConnection(Entorno.PG_REPLICA, "curso", "curso");
             var rs = r.createStatement().executeQuery("SELECT pg_is_in_recovery()")) {
            rs.next();
            assertThat(rs.getBoolean(1)).as("la réplica es de verdad una réplica").isTrue();
        }
    }

    static boolean tablaYFilasEnLaReplica() {
        try {
            return perfiles.leerDeReplica(3) != null;
        } catch (Exception laTablaAunNoLlega) {
            return false;
        }
    }

    @Test
    void la_replica_devuelve_el_dato_anterior_y_despues_converge() throws Exception {
        perfiles.guardar(1, "Ana María");
        assertThat(perfiles.leerDeReplica(1)).as("recién escrito").isEqualTo("Ana");
        long t0 = System.nanoTime();
        while (!"Ana María".equals(perfiles.leerDeReplica(1))) {
            Thread.sleep(50);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("Lectura desactualizada: la réplica convergió a los " + ms + " ms");
        assertThat(ms).isBetween(1000L, 5000L);
    }

    @Test
    void solucion_1_leer_del_lider_durante_unos_segundos_tras_escribir() throws Exception {
        perfiles.guardar(2, "Beto Ruiz");
        assertThat(perfiles.leerDeReplica(2)).isEqualTo("Beto");
        assertThat(perfiles.leerConLiderTrasEscribir(2, Duration.ofSeconds(5))).isEqualTo("Beto Ruiz");
    }

    @Test
    void solucion_2_el_cliente_envia_la_version_que_espera() throws Exception {
        String lsn = perfiles.guardar(3, "Carla Gil");
        assertThat(perfiles.leerDeReplica(3)).isEqualTo("Carla");
        long t0 = System.nanoTime();
        assertThat(perfiles.leerConVersionEsperada(3, lsn, Duration.ofSeconds(5))).isEqualTo("Carla Gil");
        System.out.println("Solución 2: con versión esperada (" + lsn + ") la réplica respondió a los " + (System.nanoTime() - t0) / 1_000_000 + " ms");
    }
}
