package nosql;

import static org.junit.jupiter.api.Assertions.*;

import comun.Entorno;
import comun.Hilos;
import comun.Salida;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import nosql.aplicacion.ErrorDeAlmacenamiento;
import nosql.aplicacion.PoliticaDeReintento;
import org.bson.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tienda.PedidosQueFallan;
import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;
import tienda.adaptadores.salida.memoria.CatalogoMutable;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.VistaDePedido;
import tienda.dominio.Dinero;
import tienda.dominio.ReglaDeNegocioViolada;

/** Pasos 4 a 8: el mismo servicio del módulo hexagonal, con memoria y con MongoDB, sin que el resto del código se entere. */
class ServicioEnAmbosAdaptadoresTest {
    static final String R = "servicio.txt";

    @ParameterizedTest
    @ValueSource(strings = {"memoria", "mongo"})
    void crearPedidoCopiaElPrecioDescuentaYAnunciaElEvento(String adaptador) {
        var s = Pruebas.nuevos(adaptador, PoliticaDeReintento.esperaCreciente(15, 2, 100));
        try {
            s.siembra().producto("teclado", "Teclado", 15_000_000, 10);
            s.siembra().producto("raton", "Ratón", 6_000_000, 3);
            var creado = s.crear().crear(Pruebas.solicitud("k-1", "ana", "teclado:2", "raton:1"));
            assertTrue(creado.nuevo());
            assertEquals(36_000_000, creado.pedido().totalCentavos());
            assertEquals(8, s.productos().porId("teclado").orElseThrow().existencias());
            s.precios().cambiar("teclado", new Dinero(99_99));                        // el pedido no se mueve con el catálogo
            assertEquals(15_000_000, s.consultar().porId(creado.pedido().id()).orElseThrow().lineas().get(0).precioUnitarioCentavos());
            var repetido = s.crear().crear(Pruebas.solicitud("k-1", "ana", "teclado:2", "raton:1"));
            assertFalse(repetido.nuevo());
            assertEquals(creado.pedido().id(), repetido.pedido().id());
            assertEquals(8, s.productos().porId("teclado").orElseThrow().existencias(), "repetir la clave no descuenta otra vez");
            assertEquals(1, s.listar().deCliente("ana").size());
            s.confirmar().confirmar(creado.pedido().id());
            assertEquals("CONFIRMADO", s.consultar().porId(creado.pedido().id()).orElseThrow().estado());
            VistaDePedido cancelado = s.cancelar().cancelar(creado.pedido().id());
            assertEquals("CANCELADO", cancelado.estado());
            assertEquals(10, s.productos().porId("teclado").orElseThrow().existencias(), "cancelar devuelve las existencias");
            assertThrows(ReglaDeNegocioViolada.class, () -> s.cancelar().cancelar(creado.pedido().id()), "una regla del negocio sigue siendo una regla del negocio");
            if (s.mongo() != null) {
                var doc = s.mongo().db().getCollection("pedidos").find(new Document("_id", creado.pedido().id())).first();
                assertEquals(3, doc.getList("historial", Document.class).size(), "PENDIENTE, CONFIRMADO y CANCELADO");
                assertEquals(3L, doc.getLong("version"), "una versión por cada guardado: crear, confirmar, cancelar");
                assertEquals(4, s.mongo().bandeja().contarPendientes(), "PedidoCreado, PrecioCambiado, PedidoConfirmado y PedidoCancelado");
            }
        } finally { s.borrarYCerrar().run(); }
    }

    /** Paso 5: un repositorio que falla a propósito entre descontar y guardar no deja nada, en ninguno de los dos adaptadores. */
    @ParameterizedTest
    @ValueSource(strings = {"memoria", "mongo"})
    void unFalloAMitadNoDejaNada(String adaptador) {
        String bd = "t04_" + UUID.randomUUID().toString().substring(0, 8);
        var s = Composicion.elegir(adaptador, Entorno.mongoUrl(), bd, null, Clock.systemUTC());
        try {
            s.siembra().producto("A", "A", 1000, 10);
            ServicioDePedidos conFallo;
            PedidosQueFallan averiado;
            if (s.mongo() != null) {
                var m = s.mongo();
                averiado = new PedidosQueFallan(m.pedidos());
                conFallo = new ServicioDePedidos(averiado, m.catalogo(), m.inventario(), m.peticiones(), m.unidad(), m.reloj(), m.bandeja());
            } else {
                // en memoria: el mismo cableado sobre un almacén propio (el del sistema no expone sus piezas)
                AlmacenEnMemoria a = new AlmacenEnMemoria();
                CatalogoMutable c = new CatalogoMutable();
                c.definir("A", "A", new Dinero(1000));
                a.ejecutar(() -> { a.guardar(tienda.dominio.Existencias.de("A", 10)); return null; });
                averiado = new PedidosQueFallan(a);
                conFallo = new ServicioDePedidos(averiado, c, a, a, a, () -> java.time.Instant.now(), a);
                s = null;
            }
            averiado.averiar();
            var servicio = conFallo;
            var e = assertThrows(IllegalStateException.class, () -> servicio.crear(Pruebas.solicitud("k-f", "ana", "A:3")));
            assertTrue(e.getMessage().contains("se perdió la conexión"));
            averiado.reparar();
            if (adaptador.equals("mongo")) {
                var db = Entorno.mongo().getDatabase(bd);
                assertEquals(10, db.getCollection("productos").find(new Document("_id", "A")).first().getInteger("existencias"), "el descuento se revirtió");
                assertEquals(0, db.getCollection("pedidos").countDocuments(), "ningún pedido");
                assertEquals(0, db.getCollection("peticiones").countDocuments(), "ninguna clave de idempotencia");
                assertEquals(0, db.getCollection("bandeja").countDocuments(), "ningún evento");
            } else {
                assertEquals(10, servicio.crear(Pruebas.solicitud("k-ok", "ana", "A:0".replace("0", "1"))).pedido().lineas().get(0).cantidad() + 9, "tras reparar, el servicio funciona");
            }
            assertTrue(servicio.crear(Pruebas.solicitud("k-f", "ana", "A:3")).nuevo(), "la misma clave se puede reintentar: no quedó registrada");
        } finally { if (s != null) s.borrarYCerrar().run(); else Entorno.mongo().getDatabase(bd).drop(); }
    }

    /** Paso 4: los errores de MongoDB llegan a la aplicación como errores de la aplicación. */
    @org.junit.jupiter.api.Test
    void erroresTraducidos() {
        var s = Pruebas.nuevos("mongo", null);
        try {
            var m = s.mongo();
            m.unidad().ejecutar(() -> { m.peticiones().registrar("dup", "p1"); return null; });
            assertThrows(ConflictoDeConcurrencia.class, () -> m.unidad().ejecutar(() -> { m.peticiones().registrar("dup", "p2"); return null; }), "clave duplicada");
            // WriteConflict y versión que no coincide: los cubre el contrato del núcleo (ContratoDelNucleoTest). Aquí, un fallo técnico:
            try (var roto = com.mongodb.client.MongoClients.create("mongodb://localhost:51998/?serverSelectionTimeoutMS=300")) {
                var unidad = new tienda.adaptadores.salida.mongo.UnidadDeTrabajoMongo(roto);
                var pedidos = new tienda.adaptadores.salida.mongo.PedidosMongo(roto.getDatabase("x"), unidad);
                assertThrows(ErrorDeAlmacenamiento.class, () -> pedidos.buscar("p"), "un MongoException no sale del adaptador");
                assertThrows(ErrorDeAlmacenamiento.class, () -> unidad.ejecutar(() -> pedidos.buscar("p")));
            }
        } finally { s.borrarYCerrar().run(); }
    }

    /** Pasos 6 y 7: 200 pedidos concurrentes sobre los mismos tres productos, con el servicio original y con espera creciente. */
    @ParameterizedTest
    @ValueSource(strings = {"memoria", "mongo"})
    void doscientosPedidosSobreTresProductos(String adaptador) {
        record Caso(String nombre, PoliticaDeReintento politica) {}
        for (Caso caso : List.of(
                new Caso("servicio original: 3 intentos sin espera", null),
                new Caso("espera creciente con azar, 15 intentos, tope 100 ms", PoliticaDeReintento.esperaCreciente(15, 2, 100)),
                new Caso("espera creciente con azar, 50 intentos, tope 1 s", PoliticaDeReintento.esperaCreciente(50, 10, 1000)))) {
            var s = Pruebas.nuevos(adaptador, caso.politica());
            try {
                for (String id : List.of("A", "B", "C")) s.siembra().producto(id, id, 1000, 120);
                AtomicInteger ok = new AtomicInteger(), sinExistencias = new AtomicInteger(), abandonados = new AtomicInteger();
                long t0 = System.nanoTime();
                Hilos.aLaVez(200, i -> () -> {
                    try { s.crear().crear(Pruebas.solicitud("k" + i, "c" + i, "A:1", "B:1", "C:1")); ok.incrementAndGet(); }
                    catch (ReglaDeNegocioViolada e) { sinExistencias.incrementAndGet(); }
                    catch (ConflictoDeConcurrencia e) { abandonados.incrementAndGet(); }
                    return null;
                });
                long ms = (System.nanoTime() - t0) / 1_000_000;
                Salida.linea(R, "%-7s %-52s realizados %3d, rechazados por existencias %3d, abandonados por conflicto %3d, %5d ms; existencias A=%d B=%d C=%d"
                        .formatted(adaptador, caso.nombre(), ok.get(), sinExistencias.get(), abandonados.get(), ms,
                                s.productos().porId("A").orElseThrow().existencias(), s.productos().porId("B").orElseThrow().existencias(), s.productos().porId("C").orElseThrow().existencias()));
                assertEquals(200, ok.get() + sinExistencias.get() + abandonados.get());
                for (String id : List.of("A", "B", "C")) assertEquals(120 - ok.get(), s.productos().porId(id).orElseThrow().existencias(), "el total de existencias cuadra en " + id);
                assertTrue(ok.get() <= 120);
                if (s.mongo() != null) {
                    assertEquals(ok.get(), s.mongo().db().getCollection("pedidos").countDocuments(), "un pedido por cada realizado");
                    assertEquals(ok.get(), s.mongo().bandeja().contarPendientes(), "un evento por cada pedido realizado");
                }
                if (caso.nombre().contains("50 intentos")) {
                    assertEquals(0, abandonados.get(), "con espera creciente, 50 intentos y tope de 1 s no se abandona ninguno");
                    assertEquals(120, ok.get());
                }
            } finally { s.borrarYCerrar().run(); }
        }
    }

    /** El tope de líneas (51) se rechaza igual con los dos adaptadores, y MongoDB lo impone además con su validador. */
    @ParameterizedTest
    @ValueSource(strings = {"memoria", "mongo"})
    void cincuentaYUnaLineasSeRechazan(String adaptador) {
        var s = Pruebas.nuevos(adaptador, null);
        try {
            s.siembra().producto("A", "A", 1, 1000);
            String[] lineas51 = java.util.stream.IntStream.range(0, 51).mapToObj(i -> "A:1").toArray(String[]::new);
            var e = assertThrows(ReglaDeNegocioViolada.class, () -> s.crear().crear(Pruebas.solicitud("k51", "ana", lineas51)));
            assertTrue(e.getMessage().contains("50"));
            assertTrue(s.crear().crear(Pruebas.solicitud("k50", "ana", java.util.Arrays.copyOf(lineas51, 50))).nuevo(), "50 líneas sí");
            assertEquals(950, s.productos().porId("A").orElseThrow().existencias());
        } finally { s.borrarYCerrar().run(); }
    }

    @org.junit.jupiter.api.Test
    void elValidadorDeMongoRechazaLoQueLaAplicacionNoVio() {
        var s = Pruebas.nuevos("mongo", null);
        try {
            var pedidos = s.mongo().db().getCollection("pedidos");
            var linea = new Document("productoId", "A").append("cantidad", 1).append("precio", 1L);
            Document ok = new Document("_id", "x1").append("clienteId", "ana").append("estado", "PENDIENTE").append("fecha", new java.util.Date()).append("total", 51L)
                    .append("lineas", java.util.Collections.nCopies(50, linea)).append("historial", List.of()).append("version", 0L);
            pedidos.insertOne(ok);
            var e = assertThrows(com.mongodb.MongoWriteException.class, () -> pedidos.insertOne(new Document(ok).append("_id", "x2").append("lineas", java.util.Collections.nCopies(51, linea))));
            assertEquals(121, e.getError().getCode(), "Document failed validation");
            assertThrows(com.mongodb.MongoWriteException.class, () -> pedidos.insertOne(new Document(ok).append("_id", "x3").append("total", -5L)));
            assertThrows(com.mongodb.MongoWriteException.class, () -> pedidos.insertOne(new Document(ok).append("_id", "x4").append("estado", "PERDIDO")));
        } finally { s.borrarYCerrar().run(); }
    }
}
