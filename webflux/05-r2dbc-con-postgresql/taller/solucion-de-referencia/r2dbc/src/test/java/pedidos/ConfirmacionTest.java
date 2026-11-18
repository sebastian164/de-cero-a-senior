package pedidos;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(properties = "consulta-lenta-ms=0")
@AutoConfigureWebTestClient
class ConfirmacionTest {
    @Autowired WebTestClient web;
    @Autowired DatabaseClient db;

    void sql(String s) { db.sql(s).fetch().rowsUpdated().block(); }
    long existencias(String p) { return db.sql("select cantidad from existencias where producto = '" + p + "'").map(f -> f.get("cantidad", Integer.class)).one().block(); }
    String estado(String id) { return db.sql("select estado from pedido where id = '" + id + "'").map(f -> f.get("estado", String.class)).one().block(); }

    @BeforeEach void datos() {
        sql("delete from clave_idempotencia where pedido_id like 't-%'");
        sql("delete from linea_pedido where pedido_id like 't-%'");
        sql("delete from pedido where id like 't-%'");
        sql("delete from existencias where producto like 't-%'");
        sql("insert into existencias values ('t-a', 10), ('t-b', 10), ('t-c', 0), ('t-ultima', 1)");
        sql("insert into pedido (id) values ('t-atomico'), ('t-u1'), ('t-u2'), ('t-ok'), ('t-idem')");
        sql("insert into pedido (id, estado) values ('t-rechazado', 'RECHAZADO')");
        sql("insert into pedido (id) values ('t-dup'), ('t-conc')");
        sql("insert into linea_pedido (pedido_id, producto, cantidad) values ('t-dup','t-b',1), ('t-conc','t-b',1)");
        sql("insert into linea_pedido (pedido_id, producto, cantidad) values ('t-atomico','t-a',2), ('t-atomico','t-b',3), ('t-atomico','t-c',1)");
        sql("insert into linea_pedido (pedido_id, producto, cantidad) values ('t-u1','t-ultima',1), ('t-u2','t-ultima',1)");
        sql("insert into linea_pedido (pedido_id, producto, cantidad) values ('t-ok','t-a',2), ('t-idem','t-b',4)");
    }

    @Test void atomico_elTercerProductoSinExistenciasNoDejaRastro() {
        web.post().uri("/pedidos/t-atomico/confirmar").exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.detail").isEqualTo("sin existencias de t-c");
        assertEquals(10, existencias("t-a"), "no se debió descontar el primero");
        assertEquals(10, existencias("t-b"), "no se debió descontar el segundo");
        assertEquals("PENDIENTE", estado("t-atomico"));
    }

    @Test void confirmaYDescuenta() {
        web.post().uri("/pedidos/t-ok/confirmar").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("CONFIRMADO");
        assertEquals(8, existencias("t-a"));
        assertEquals("CONFIRMADO", estado("t-ok"));
    }

    @Test void pedidoInexistenteEs404() { web.post().uri("/pedidos/t-nada/confirmar").exchange().expectStatus().isNotFound(); }

    @Test void laUltimaUnidadSeVendeUnaVez() {
        // lanzadas a la vez desde dos hilos
        var a = CompletableFuture.supplyAsync(() -> web.post().uri("/pedidos/t-u1/confirmar").exchange().returnResult(String.class).getStatus().value());
        var b = CompletableFuture.supplyAsync(() -> web.post().uri("/pedidos/t-u2/confirmar").exchange().returnResult(String.class).getStatus().value());
        List<Integer> r = List.of(a.join(), b.join());
        assertTrue(r.contains(200) && r.contains(409), "debía ganar uno solo: " + r);
        assertEquals(0, existencias("t-ultima"));
    }

    long claves(String pedido) { return db.sql("select count(*) as n from clave_idempotencia where pedido_id = '" + pedido + "'").map(f -> f.get("n", Long.class)).one().block(); }

    @Test void unaClaveYaProcesadaDevuelveREPETIDAYNoDescuenta() {
        sql("insert into clave_idempotencia (clave, pedido_id) values ('k-previa', 't-dup')");           // la clave ya se usó antes
        web.post().uri("/pedidos/t-dup/confirmar").header("Idempotency-Key", "k-previa").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("REPETIDA");
        assertEquals(10, existencias("t-b"), "no se descontó");
        assertEquals("PENDIENTE", estado("t-dup"), "la transacción se revirtió entera: tampoco cambió el estado");
        assertEquals(1, claves("t-dup"), "sigue habiendo una sola fila de clave");
    }

    @Test void dosPeticionesALaVezConLaMismaClaveDescuentanUnaVez() {
        var a = CompletableFuture.supplyAsync(() -> web.post().uri("/pedidos/t-conc/confirmar").header("Idempotency-Key", "k-conc").exchange().returnResult(String.class).getResponseBody().blockFirst());
        var b = CompletableFuture.supplyAsync(() -> web.post().uri("/pedidos/t-conc/confirmar").header("Idempotency-Key", "k-conc").exchange().returnResult(String.class).getResponseBody().blockFirst());
        List<String> r = List.of(a.join(), b.join());
        assertEquals(1, r.stream().filter("CONFIRMADO"::equals).count(), "solo una confirma de verdad: " + r);
        assertTrue(r.stream().allMatch(x -> List.of("CONFIRMADO", "REPETIDA", "YA_CONFIRMADO").contains(x)), r.toString());
        assertEquals(9, existencias("t-b"), "se descontó una sola vez");
        assertEquals(1, claves("t-conc"), "una sola fila de clave");
    }

    @Test void unPedidoRechazadoNoSeLlamaYaConfirmado() {
        web.post().uri("/pedidos/t-rechazado/confirmar").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("RECHAZADO");
    }

    @Test void laMismaClaveNoDescuentaDosVeces() {
        for (int i = 0; i < 2; i++) web.post().uri("/pedidos/t-idem/confirmar").header("Idempotency-Key", "k-1").exchange().expectStatus().isOk();
        assertEquals(6, existencias("t-b"));
    }
}
