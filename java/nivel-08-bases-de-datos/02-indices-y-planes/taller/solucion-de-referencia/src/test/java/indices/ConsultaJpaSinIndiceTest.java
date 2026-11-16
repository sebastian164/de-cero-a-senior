package indices;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.util.Locale;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import soporte.Entorno;

/** Paso 7: registro de SQL activado en Spring, y una consulta de JPA que no aprovecha el índice existente. */
@Order(2)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(classes = AplicacionPedidos.class, properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:43432/n8_02",
    "spring.datasource.username=curso",
    "spring.datasource.password=curso",
    "spring.jpa.hibernate.ddl-auto=none",
    "logging.level.org.hibernate.SQL=debug",
    "spring.jpa.properties.hibernate.session_factory.statement_inspector=indices.CapturaSql"
})
class ConsultaJpaSinIndiceTest {

    @Autowired PedidoRepositorio repositorio;

    @Test
    void la_consulta_derivada_ignore_case_no_usa_el_indice_de_lower(CapturedOutput salida) throws Exception {
        CapturaSql.SENTENCIAS.clear();
        assertThat(repositorio.findByCorreoIgnoreCase("CLIENTE4242@correo.co")).isNotEmpty();
        String sql = CapturaSql.SENTENCIAS.getLast();
        assertThat(salida.getOut()).contains("org.hibernate.SQL");       // el registro de SQL está activo
        assertThat(sql).containsIgnoringCase("upper(");                  // Spring Data usa upper(), el índice es lower()

        String literal = sql.replace("?", "'CLIENTE4242@correo.co'");
        try (Connection c = Entorno.conectar(Entorno.url("n8_02"))) {
            Planes.Plan sinIndice = Planes.explicar(c, literal);
            assertThat(sinIndice.texto()).contains("Seq Scan on pedidos");

            Planes.ejecutar(c, "CREATE INDEX idx_pedidos_correo_upper ON pedidos (upper(correo))");
            Planes.ejecutar(c, "ANALYZE pedidos");
            Planes.Plan conIndice = Planes.explicar(c, literal);
            assertThat(conIndice.texto()).contains("idx_pedidos_correo_upper");
            Planes.ejecutar(c, "DROP INDEX idx_pedidos_correo_upper");

            Files.writeString(Path.of("target/mediciones.md"), String.format(Locale.ROOT,
                    "Consulta JPA `findByCorreoIgnoreCase`: Hibernate envía `%s`. Con el índice sobre lower(correo): %s, %.1f ms. "
                    + "Con un índice sobre upper(correo): %.3f ms.%n%n",
                    sql, IndicesTest.nodo(sinIndice), sinIndice.milisegundos(), conIndice.milisegundos()),
                    StandardOpenOption.APPEND);
        }
    }
}
