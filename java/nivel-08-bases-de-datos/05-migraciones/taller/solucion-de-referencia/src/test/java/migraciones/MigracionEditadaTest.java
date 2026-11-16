package migraciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import soporte.Entorno;

/** Paso 2: editar una migración ya aplicada. */
class MigracionEditadaTest {

    @TempDir Path carpeta;

    Flyway flyway(String url) {
        return Flyway.configure().dataSource(url, Entorno.USUARIO, Entorno.CLAVE)
                .locations("filesystem:" + carpeta).load();
    }

    @Test
    void cambiar_una_migracion_aplicada_rompe_la_validacion_al_arrancar() throws Exception {
        for (String m : new String[] {"V1__crear_clientes.sql", "V2__crear_pedidos.sql", "V3__agregar_estado_a_pedidos.sql"}) {
            Files.copy(Path.of("src/main/resources/db/migration", m), carpeta.resolve(m));
        }
        String url = Entorno.baseNueva("n8_05_editada");
        assertThat(flyway(url).migrate().migrationsExecuted).isEqualTo(3);

        // Alguien "corrige" V2 en lugar de escribir una V4.
        Files.writeString(carpeta.resolve("V2__crear_pedidos.sql"),
                "\nCREATE INDEX idx_pedidos_fecha ON pedidos (creado_en);\n", StandardOpenOption.APPEND);

        assertThatThrownBy(() -> flyway(url).migrate())     // validateOnMigrate=true por defecto: así arranca Spring Boot
                .isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("checksum mismatch")
                .hasMessageContaining("version 2")
                .satisfies(e -> System.out.println("Error de Flyway:\n" + e.getMessage()));
    }
}
