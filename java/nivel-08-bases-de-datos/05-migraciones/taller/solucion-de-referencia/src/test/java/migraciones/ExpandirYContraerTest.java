package migraciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import migraciones.VersionesDeLaAplicacion.Version;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import soporte.Entorno;

/**
 * Paso 3: renombrar nombre → nombre_completo en cuatro despliegues. Tras cada migración se comprueba
 * que la versión anterior de la aplicación (todavía en ejecución durante el despliegue) y la nueva funcionan.
 */
class ExpandirYContraerTest {

    @Test
    void cuatro_despliegues_compatibles_con_la_version_anterior() {
        String url = Entorno.baseNueva("n8_05_expandir");
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(url);
        ds.setUser(Entorno.USUARIO);
        ds.setPassword(Entorno.CLAVE);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        List<Long> creados = new ArrayList<>();

        migrarHasta(url, "1");
        Version v1 = VersionesDeLaAplicacion.v1(jdbc);
        creados.add(funciona(v1, "Ana Pérez"));

        // Despliegue 1: expandir. Conviven v1 (vieja) y v2 (nueva).
        migrarHasta(url, "2");
        Version v2 = VersionesDeLaAplicacion.v2(jdbc);
        creados.add(funciona(v1, "Beto Ruiz"));
        creados.add(funciona(v2, "Carla Díaz"));

        // Despliegue 2: rellenar. Conviven v2 y v3 (que ya lee la columna nueva).
        migrarHasta(url, "3");
        Version v3 = VersionesDeLaAplicacion.v3(jdbc);
        creados.add(funciona(v2, "Dora Gil"));
        creados.add(funciona(v3, "Elena Mora"));
        for (Long id : creados) {
            assertThat(v3.leer(id)).as("v3 lee lo que escribieron v1 y v2").isNotNull();
        }

        // Despliegue 3: cambiar. Conviven v3 y v4 (que ya no escribe "nombre").
        migrarHasta(url, "4");
        Version v4 = VersionesDeLaAplicacion.v4(jdbc);
        creados.add(funciona(v3, "Fabio León"));
        long deV4 = funciona(v4, "Gina Paz");
        creados.add(deV4);
        assertThat(v3.leer(deV4)).isEqualTo("Gina Paz");

        // Despliegue 4: contraer. Solo queda v4 en ejecución; v3 ya se retiró.
        migrarHasta(url, "5");
        creados.add(funciona(v4, "Hugo Sanz"));
        for (Long id : creados) {
            assertThat(v4.leer(id)).isNotBlank();
        }
        // Si la contracción se adelantara con v3 todavía viva, v3 fallaría: por eso va en un despliegue posterior.
        assertThatThrownBy(() -> v3.crear("Iris")).hasMessageContaining("nombre");
    }

    static long funciona(Version v, String nombre) {
        long id = v.crear(nombre);
        assertThat(v.leer(id)).isEqualTo(nombre);
        return id;
    }

    static void migrarHasta(String url, String version) {
        Flyway.configure().dataSource(url, Entorno.USUARIO, Entorno.CLAVE)
                .locations("classpath:expandir").target(version).load().migrate();
    }
}
