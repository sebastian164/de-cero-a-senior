package indices;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import soporte.Entorno;

@Order(1)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IndicesTest {

    static final String BASE = "n8_02";
    static Connection c;
    static final StringBuilder informe = new StringBuilder("# Mediciones n8-02 (generado por IndicesTest)\n\n");

    static final Map<String, String> CONSULTAS = new LinkedHashMap<>();
    static {
        CONSULTAS.put("Q1 pendientes recientes de un cliente",
                "SELECT id, total FROM pedidos WHERE cliente_id = 4242 AND estado = 'PENDIENTE' ORDER BY creado_en DESC LIMIT 20");
        CONSULTAS.put("Q2 pedidos por correo sin importar mayúsculas",
                "SELECT id, estado FROM pedidos WHERE lower(correo) = 'cliente4242@correo.co'");
        CONSULTAS.put("Q3 total vendido en un día",
                "SELECT sum(total) FROM pedidos WHERE creado_en >= '2026-06-01 00:00-05' AND creado_en < '2026-06-02 00:00-05'");
        CONSULTAS.put("Q4 historial de 90 días de un cliente (solo índice)",
                "SELECT creado_en, total FROM pedidos WHERE cliente_id = 4242 AND creado_en >= '2026-08-17 00:00-05' ORDER BY creado_en DESC");
        CONSULTAS.put("Q5 los diez pendientes más antiguos",
                "SELECT id FROM pedidos WHERE estado = 'PENDIENTE' ORDER BY creado_en LIMIT 10");
    }
    static final Map<String, Planes.Plan> antes = new LinkedHashMap<>();

    @BeforeAll
    static void cargar() throws Exception {
        c = Entorno.conectar(Entorno.baseNueva(BASE));
        long t0 = System.nanoTime();
        Entorno.ejecutarArchivo(c, Path.of("src/main/resources/carga.sql"));
        linea("Carga de 1.000.000 de filas: %.1f s", (System.nanoTime() - t0) / 1e9);
    }

    @AfterAll
    static void escribir() throws Exception {
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/mediciones.md"), informe);
        c.close();
    }

    static void linea(String formato, Object... args) {
        informe.append(String.format(Locale.ROOT, formato, args)).append("\n\n");
    }

    @Test
    @Order(1)
    void sin_indices_las_cinco_consultas_recorren_la_tabla() throws Exception {
        for (var e : CONSULTAS.entrySet()) {
            Planes.Plan p = Planes.explicar(c, e.getValue());
            antes.put(e.getKey(), p);
            assertThat(p.texto()).as(e.getKey()).contains("Seq Scan on pedidos");
        }
    }

    @Test
    @Order(2)
    void con_los_indices_minimos_cada_consulta_usa_el_suyo() throws Exception {
        long t0 = System.nanoTime();
        Entorno.ejecutarArchivo(c, Path.of("src/main/resources/indices.sql"));
        linea("Creación de los cuatro índices: %.1f s", (System.nanoTime() - t0) / 1e9);
        String[] esperado = {
            "idx_pedidos_cliente_fecha", "idx_pedidos_correo_lower", "idx_pedidos_creado_en",
            "Index Only Scan using idx_pedidos_cliente_fecha", "idx_pedidos_pendientes"};
        informe.append("| Consulta | Antes (ms) | Plan antes | Después (ms) | Plan después |\n|---|---:|---|---:|---|\n");
        int i = 0;
        for (var e : CONSULTAS.entrySet()) {
            Planes.Plan p = Planes.explicar(c, e.getValue());
            Planes.Plan a = antes.get(e.getKey());
            assertThat(p.texto()).as(e.getKey()).contains(esperado[i++]).doesNotContain("Seq Scan");
            assertThat(p.milisegundos()).as(e.getKey()).isLessThan(a.milisegundos());
            informe.append(String.format(Locale.ROOT, "| %s | %.2f | %s | %.3f | %s |%n", e.getKey(), a.milisegundos(),
                    nodo(a), p.milisegundos(), nodo(p)));
        }
        informe.append('\n');
    }

    static String nodo(Planes.Plan p) {
        String linea = p.texto().lines().filter(l -> l.contains("Index") && l.contains("Scan")).findFirst()
                .orElse(p.texto().lines().filter(l -> l.contains("Scan")).findFirst().orElse("?"));
        return java.util.stream.Stream.of(linea).map(String::strip)
                .map(l -> l.replaceAll("^->\\s*", "").replaceAll("\\s+\\(cost.*", "")).findFirst().orElse("?");
    }

    @Test
    @Order(3)
    void los_indices_hacen_mas_lenta_la_insercion() throws Exception {
        String insertar = """
            INSERT INTO %s (cliente_id, correo, estado, canal, total, creado_en)
            SELECT 1 + (hashint4(g) & 2147483647) %% 100000, 'Nuevo' || g || '@correo.co', 'PENDIENTE', 'WEB', 1000, now() - g * interval '1 second'
            FROM generate_series(1, 100000) g""";
        Planes.ejecutar(c, "CREATE TABLE pedidos_sin_indices (LIKE pedidos INCLUDING DEFAULTS INCLUDING IDENTITY)");
        Planes.ejecutar(c, "ALTER TABLE pedidos_sin_indices ADD PRIMARY KEY (id)");
        double sin = cronometrar(insertar.formatted("pedidos_sin_indices"));
        double con = cronometrar(insertar.formatted("pedidos"));
        linea("Insertar 100.000 filas: %.0f ms solo con la clave primaria, %.0f ms con la clave primaria y los cuatro índices (x%.1f)",
                sin, con, con / sin);
        assertThat(con).isGreaterThan(sin);
        Planes.ejecutar(c, "DELETE FROM pedidos WHERE correo LIKE 'Nuevo%'");
        Planes.ejecutar(c, "VACUUM ANALYZE pedidos");
    }

    static double cronometrar(String sql) throws Exception {
        long t0 = System.nanoTime();
        Planes.ejecutar(c, sql);
        return (System.nanoTime() - t0) / 1e6;
    }

    @Test
    @Order(4)
    void un_indice_que_existe_y_no_se_usa() throws Exception {
        // Caso A: baja selectividad. El 70 % de las filas está ENTREGADO: leer el índice y luego saltar a la tabla
        // es más caro que recorrerla entera, y el planificador lo sabe por las estadísticas.
        Planes.ejecutar(c, "CREATE INDEX idx_pedidos_estado ON pedidos (estado)");
        Planes.ejecutar(c, "ANALYZE pedidos");
        Planes.Plan baja = Planes.explicar(c, "SELECT count(*), sum(total) FROM pedidos WHERE estado = 'ENTREGADO'");
        assertThat(baja.texto()).contains("Seq Scan on pedidos").doesNotContain("idx_pedidos_estado");
        Planes.Plan alta = Planes.explicar(c, "SELECT count(*), sum(total) FROM pedidos WHERE estado = 'PENDIENTE'");
        assertThat(alta.texto()).doesNotContain("Seq Scan");
        linea("Índice sobre estado: con 'ENTREGADO' (70 %%) el plan es `%s` (%.1f ms); con 'PENDIENTE' (2 %%) usa índice (%.1f ms).",
                nodo(baja), baja.milisegundos(), alta.milisegundos());

        // Caso B: función sobre la columna indexada. El índice de creado_en no sirve para date(creado_en).
        Planes.Plan funcion = Planes.explicar(c,
                "SELECT count(*) FROM pedidos WHERE date(creado_en AT TIME ZONE 'America/Bogota') = '2026-06-01'");
        assertThat(funcion.texto()).contains("Seq Scan on pedidos");
        Planes.Plan rango = Planes.explicar(c, CONSULTAS.get("Q3 total vendido en un día").replace("sum(total)", "count(*)"));
        assertThat(rango.texto()).doesNotContain("Seq Scan");
        linea("date(creado_en) = día: `%s` (%.1f ms). El mismo día como rango sobre la columna: índice (%.2f ms).",
                nodo(funcion), funcion.milisegundos(), rango.milisegundos());
        Planes.ejecutar(c, "DROP INDEX idx_pedidos_estado");
    }

    @Test
    @Order(5)
    void offset_grande_frente_a_cursor() throws Exception {
        Planes.Plan offset = Planes.explicar(c, "SELECT id, total FROM pedidos ORDER BY id LIMIT 20 OFFSET 900000");
        Planes.Plan cursor = Planes.explicar(c, "SELECT id, total FROM pedidos WHERE id > 900000 ORDER BY id LIMIT 20");
        assertThat(cursor.milisegundos() * 20).isLessThan(offset.milisegundos());
        String filas = offset.texto().lines().filter(l -> l.contains("Index Scan")).findFirst().orElse("").strip();
        linea("Paginación: OFFSET 900000 = %.1f ms (`%s`); cursor `WHERE id > 900000` = %.3f ms.",
                offset.milisegundos(), filas.replaceAll("\\(cost=[^)]*\\) ", ""), cursor.milisegundos());
    }
}
