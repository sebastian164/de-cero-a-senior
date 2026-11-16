package cache;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.limit;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Aggregates.unwind;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import soporte.Entorno;

/**
 * Paso 5: el mismo blog en PostgreSQL (relacional) y en MongoDB (documentos), con los mismos datos.
 * Cada prueba responde la misma pregunta en los dos modelos y compara el resultado.
 */
class BlogModelosTest {

    static Connection pg;
    static MongoClient mongoCliente;
    static MongoCollection<Document> entradas;

    record Comentario(String autor, String texto) {}
    record Entrada(int id, String titulo, String autor, List<String> etiquetas, List<Comentario> comentarios) {}

    static final List<Entrada> DATOS = List.of(
            new Entrada(1, "Índices en PostgreSQL", "ana", List.of("sql", "rendimiento"),
                    List.of(new Comentario("luis", "Muy útil"), new Comentario("eva", "¿Y los parciales?"))),
            new Entrada(2, "Cachés con Redis", "luis", List.of("redis", "rendimiento", "cache"),
                    List.of(new Comentario("ana", "Ojo con la estampida"))),
            new Entrada(3, "Modelar por consultas", "eva", List.of("nosql", "mongodb"),
                    List.of(new Comentario("luis", "Gran resumen"), new Comentario("ana", "Faltó el esquema"), new Comentario("luis", "Cierto"))),
            new Entrada(4, "Transacciones", "ana", List.of("sql"), List.of()));

    @BeforeAll
    static void cargar() throws Exception {
        pg = Entorno.conectar(Entorno.baseNueva("n8_06_blog"));
        sql("""
            CREATE TABLE autores (usuario TEXT PRIMARY KEY);
            CREATE TABLE entradas (id INT PRIMARY KEY, titulo TEXT NOT NULL, autor TEXT NOT NULL REFERENCES autores);
            CREATE TABLE etiquetas (id SERIAL PRIMARY KEY, nombre TEXT NOT NULL UNIQUE);
            CREATE TABLE entradas_etiquetas (entrada_id INT REFERENCES entradas, etiqueta_id INT REFERENCES etiquetas,
                                             PRIMARY KEY (entrada_id, etiqueta_id));
            CREATE INDEX ON entradas_etiquetas (etiqueta_id);
            CREATE TABLE comentarios (id SERIAL PRIMARY KEY, entrada_id INT NOT NULL REFERENCES entradas,
                                      autor TEXT NOT NULL REFERENCES autores, texto TEXT NOT NULL);
            CREATE INDEX ON comentarios (entrada_id);
            CREATE INDEX ON comentarios (autor);
            INSERT INTO autores VALUES ('ana'), ('luis'), ('eva');
            """);
        mongoCliente = MongoClients.create(Entorno.MONGO_URI);
        var db = mongoCliente.getDatabase("n8_06_blog");
        db.drop();
        entradas = db.getCollection("entradas");
        entradas.createIndex(Indexes.ascending("etiquetas"));            // índice multivalor
        entradas.createIndex(Indexes.ascending("comentarios.autor"));

        for (Entrada e : DATOS) {
            sql("INSERT INTO entradas VALUES (" + e.id() + ", '" + e.titulo() + "', '" + e.autor() + "')");
            for (String t : e.etiquetas()) {
                sql("INSERT INTO etiquetas (nombre) VALUES ('" + t + "') ON CONFLICT DO NOTHING");
                sql("INSERT INTO entradas_etiquetas SELECT " + e.id() + ", id FROM etiquetas WHERE nombre = '" + t + "'");
            }
            for (Comentario c : e.comentarios()) {
                sql("INSERT INTO comentarios (entrada_id, autor, texto) VALUES (" + e.id() + ", '" + c.autor() + "', '" + c.texto() + "')");
            }
            // Documento: la entrada se lee completa (con comentarios y etiquetas) en un solo acceso.
            entradas.insertOne(new Document("_id", e.id()).append("titulo", e.titulo()).append("autor", e.autor())
                    .append("etiquetas", e.etiquetas())
                    .append("comentarios", e.comentarios().stream().map(c -> new Document("autor", c.autor()).append("texto", c.texto())).toList()));
        }
    }

    @AfterAll
    static void cerrar() throws SQLException {
        pg.close();
        mongoCliente.close();
    }

    static void sql(String s) throws SQLException {
        try (Statement st = pg.createStatement()) {
            st.execute(s);
        }
    }

    static List<String> columna(String consulta) throws SQLException {
        List<String> r = new ArrayList<>();
        try (Statement st = pg.createStatement(); ResultSet rs = st.executeQuery(consulta)) {
            while (rs.next()) {
                r.add(rs.getString(1));
            }
        }
        return r;
    }

    @Test
    void entrada_completa_facil_en_documentos_tres_consultas_o_uniones_en_relacional() throws SQLException {
        Document doc = entradas.find(eq("_id", 3)).first();                     // un acceso
        List<String> textosDoc = doc.getList("comentarios", Document.class).stream().map(d -> d.getString("texto")).toList();
        List<String> textosSql = columna("SELECT texto FROM comentarios WHERE entrada_id = 3 ORDER BY id");
        List<String> etiquetasSql = columna("""
            SELECT t.nombre FROM entradas_etiquetas et JOIN etiquetas t ON t.id = et.etiqueta_id WHERE et.entrada_id = 3 ORDER BY t.nombre""");
        assertThat(textosDoc).isEqualTo(textosSql);
        assertThat(doc.getList("etiquetas", String.class)).containsExactlyInAnyOrderElementsOf(etiquetasSql);
    }

    @Test
    void entradas_por_etiqueta_facil_en_ambos() throws SQLException {
        List<Integer> doc = entradas.find(eq("etiquetas", "rendimiento")).sort(ascending("_id")).map(d -> d.getInteger("_id")).into(new ArrayList<>());
        List<String> rel = columna("""
            SELECT et.entrada_id FROM entradas_etiquetas et JOIN etiquetas t ON t.id = et.etiqueta_id
            WHERE t.nombre = 'rendimiento' ORDER BY 1""");
        assertThat(doc).containsExactly(1, 2);
        assertThat(rel).containsExactly("1", "2");
    }

    @Test
    void comentarios_de_un_autor_facil_en_relacional_agregacion_en_documentos() throws SQLException {
        List<String> rel = columna("SELECT texto FROM comentarios WHERE autor = 'luis' ORDER BY id");
        // En documentos los comentarios están dentro de cada entrada: hay que desenrollarlos y filtrar.
        List<String> doc = entradas.aggregate(List.of(
                match(eq("comentarios.autor", "luis")), unwind("$comentarios"), match(eq("comentarios.autor", "luis")),
                sort(ascending("_id")))).map(d -> d.get("comentarios", Document.class).getString("texto")).into(new ArrayList<>());
        assertThat(doc).isEqualTo(rel).containsExactly("Muy útil", "Gran resumen", "Cierto");
    }

    @Test
    void etiquetas_mas_usadas_group_by_frente_a_unwind_y_group() throws SQLException {
        List<String> rel = columna("""
            SELECT t.nombre FROM entradas_etiquetas et JOIN etiquetas t ON t.id = et.etiqueta_id
            GROUP BY t.nombre ORDER BY count(*) DESC, t.nombre LIMIT 2""");
        List<String> doc = entradas.aggregate(List.of(unwind("$etiquetas"), group("$etiquetas", sum("n", 1)),
                sort(descending("n").toBsonDocument().append("_id", new org.bson.BsonInt32(1))), limit(2)))
                .map(d -> d.getString("_id")).into(new ArrayList<>());
        assertThat(doc).isEqualTo(rel).containsExactly("rendimiento", "sql");
    }

    @Test
    void renombrar_una_etiqueta_una_fila_frente_a_todos_los_documentos() throws SQLException {
        sql("UPDATE etiquetas SET nombre = 'performance' WHERE nombre = 'rendimiento'");      // un dato, un lugar
        var r = entradas.updateMany(eq("etiquetas", "rendimiento"), Updates.set("etiquetas.$[e]", "performance"),
                new UpdateOptions().arrayFilters(List.of(eq("e", "rendimiento"))));            // copia en cada documento
        assertThat(r.getModifiedCount()).isEqualTo(2);
        assertThat(entradas.countDocuments(eq("etiquetas", "performance"))).isEqualTo(2);
        sql("UPDATE etiquetas SET nombre = 'rendimiento' WHERE nombre = 'performance'");
        entradas.updateMany(eq("etiquetas", "performance"), Updates.set("etiquetas.$[e]", "rendimiento"),
                new UpdateOptions().arrayFilters(List.of(eq("e", "performance"))));
    }

    @Test
    void integridad_la_base_relacional_rechaza_un_autor_inexistente_mongo_lo_acepta() {
        try {
            sql("INSERT INTO comentarios (entrada_id, autor, texto) VALUES (1, 'fantasma', 'hola')");
            throw new AssertionError("debió fallar");
        } catch (SQLException e) {
            assertThat(e.getSQLState()).isEqualTo("23503");
        }
        var r = entradas.updateOne(eq("_id", 1), Updates.push("comentarios", new Document("autor", "fantasma").append("texto", "hola")));
        assertThat(r.getModifiedCount()).isEqualTo(1);                    // sin validación de esquema, nada lo impide
        entradas.updateOne(eq("_id", 1), Updates.pull("comentarios", new Document("autor", "fantasma")));
    }
}
