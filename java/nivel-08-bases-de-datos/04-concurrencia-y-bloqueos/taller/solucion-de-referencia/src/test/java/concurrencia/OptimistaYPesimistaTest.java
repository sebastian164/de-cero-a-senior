package concurrencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import soporte.Entorno;

class OptimistaYPesimistaTest extends BaseConcurrencia {

    @LocalServerPort int puerto;
    @Autowired ServicioProductos servicio;
    @Autowired ServicioConReintento conReintento;
    @Autowired PlatformTransactionManager tm;

    final HttpClient http = HttpClient.newHttpClient();

    HttpResponse<String> enviar(HttpRequest.Builder b) throws Exception {
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void paso1_dos_transacciones_chocan_por_version() {
        TransactionTemplate a = new TransactionTemplate(tm);
        TransactionTemplate b = new TransactionTemplate(tm);
        b.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> a.executeWithoutResult(s -> {
            Producto enA = repositorio.findById(1L).orElseThrow();            // A lee versión 0
            b.executeWithoutResult(s2 -> repositorio.findById(1L).orElseThrow().fijarPrecio(new BigDecimal("110")));  // B confirma versión 1
            enA.fijarPrecio(new BigDecimal("120"));                            // A escribe sobre versión 0
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(repositorio.findById(1L).orElseThrow().getPrecio()).isEqualByComparingTo("110");
    }

    @Test
    void paso1_por_http_el_segundo_en_guardar_recibe_409_con_un_mensaje_util() throws Exception {
        String base = "http://localhost:" + puerto + "/productos/1";
        HttpResponse<String> lecturaAna = enviar(HttpRequest.newBuilder(URI.create(base)));
        HttpResponse<String> lecturaLuis = enviar(HttpRequest.newBuilder(URI.create(base)));
        String etag = lecturaAna.headers().firstValue("ETag").orElseThrow();
        assertThat(lecturaLuis.headers().firstValue("ETag")).contains(etag);

        HttpResponse<String> guardaAna = enviar(HttpRequest.newBuilder(URI.create(base + "/precio"))
                .header("If-Match", etag).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"precio\": 110}")));
        assertThat(guardaAna.statusCode()).isEqualTo(200);
        assertThat(guardaAna.headers().firstValue("ETag")).isPresent().get().isNotEqualTo(etag);

        HttpResponse<String> guardaLuis = enviar(HttpRequest.newBuilder(URI.create(base + "/precio"))
                .header("If-Match", etag).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"precio\": 90}")));
        assertThat(guardaLuis.statusCode()).isEqualTo(409);
        assertThat(guardaLuis.headers().firstValue("Content-Type")).contains("application/problem+json");
        assertThat(guardaLuis.body()).contains("Otro usuario modificó este producto").contains("Edición concurrente");
        System.out.println("409 → " + guardaLuis.body());
    }

    @Test
    void paso2_reintento_de_tres_intentos_para_una_operacion_idempotente() throws Exception {
        conReintento.intentos().set(0);
        conReintento.sesiones().clear();
        Paralelo.Resultado r = Paralelo.correr(3, i -> conReintento.fijarPrecio(1L, new BigDecimal(200 + i)));
        // Tres editores simultáneos: cada uno puede chocar como mucho con los otros dos, así que tres intentos bastan.
        assertThat(r.exitos()).isEqualTo(3);
        System.out.println("Paso 2: 3 operaciones, " + conReintento.intentos().get() + " intentos en total");
        assertThat(conReintento.intentos().get()).isGreaterThan(3);
        // El reintento envuelve a la transacción: cada intento (también los que chocan) corre en una sesión de persistencia nueva.
        assertThat(conReintento.sesiones()).as("una transacción nueva por intento").hasSize(conReintento.intentos().get()).doesNotHaveDuplicates();
    }

    @Test
    void paso3_bajo_carga_de_50_hilos_optimista_frente_a_pesimista() throws Exception {
        Paralelo.Resultado optimista = Paralelo.correr(50, i -> servicio.venderOptimista(1L));
        int tras1 = repositorio.findById(1L).orElseThrow().getStock();
        producto();
        conReintento.intentos().set(0);
        Paralelo.Resultado reintento = Paralelo.correr(50, i -> conReintento.venderOptimistaConReintento(1L));
        int tras2 = repositorio.findById(1L).orElseThrow().getStock();
        producto();
        Paralelo.Resultado pesimista = Paralelo.correr(50, i -> servicio.venderPesimista(1L));
        int tras3 = repositorio.findById(1L).orElseThrow().getStock();

        System.out.printf("Paso 3 | optimista sin reintento: %d ventas, %d conflictos, stock %d, %d ms%n",
                optimista.exitos(), optimista.fallos(), tras1, optimista.milisegundos());
        System.out.printf("Paso 3 | optimista con 3 intentos: %d ventas, %d fallidas, %d intentos, stock %d, %d ms%n",
                reintento.exitos(), reintento.fallos(), conReintento.intentos().get(), tras2, reintento.milisegundos());
        System.out.printf("Paso 3 | pesimista: %d ventas, %d fallidas, stock %d, %d ms%n",
                pesimista.exitos(), pesimista.fallos(), tras3, pesimista.milisegundos());

        // Ninguna variante pierde ventas: lo que se confirma coincide con el stock.
        assertThat(100 - tras1).isEqualTo(optimista.exitos());
        assertThat(100 - tras2).isEqualTo(reintento.exitos());
        assertThat(optimista.fallos()).isPositive();
        assertThat(optimista.errores()).allMatch(e -> e instanceof ObjectOptimisticLockingFailureException);
        assertThat(reintento.exitos()).isGreaterThan(optimista.exitos());
        assertThat(pesimista.exitos()).isEqualTo(50);
        assertThat(tras3).isEqualTo(50);
    }

    @Test
    void el_bloqueo_pesimista_respeta_el_tiempo_limite_de_3_segundos() throws Exception {
        try (Connection otra = Entorno.conectar(URL)) {
            otra.setAutoCommit(false);
            otra.createStatement().execute("SELECT * FROM productos WHERE id = 1 FOR UPDATE");   // retiene el bloqueo
            long t0 = System.nanoTime();
            assertThatThrownBy(() -> servicio.venderPesimista(1L)).isInstanceOf(PessimisticLockingFailureException.class);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("Espera hasta el error de bloqueo: " + ms + " ms");
            assertThat(ms).isBetween(2500L, 6000L);
            otra.rollback();
        }
    }
}
