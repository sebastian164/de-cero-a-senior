package tienda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tienda.Dobles.CaidaDelProceso;
import tienda.Dobles.Comun;
import tienda.Dobles.EnviosDoble;
import tienda.Dobles.Fallo;
import tienda.Dobles.InventarioDoble;
import tienda.Dobles.PagosDoble;
import tienda.adaptadores.salida.http.PagosHttp;
import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;
import tienda.adaptadores.salida.memoria.CatalogoEnMemoria;
import tienda.adaptadores.salida.memoria.SagasEnMemoria;
import tienda.adaptadores.salida.sistema.RelojDelSistema;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.VistaDePedido;
import tienda.aplicacion.puertos.entrada.ConfirmarPedido;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.aplicacion.saga.Puertos.ConfirmarCompra.Compra;
import tienda.aplicacion.saga.Puertos.Pagos;
import tienda.aplicacion.saga.ServicioDeCompras;
import tienda.dominio.Dinero;
import tienda.dominio.Existencias;
import tienda.dominio.saga.SagaDeCompra.Estado;

class ServicioDeComprasTest {

    /** Decorador de la unidad de trabajo para las pruebas: la n-ésima transacción muere antes de confirmar. */
    static final class UnidadFallible implements UnidadDeTrabajo {
        private final SagasEnMemoria.Unidad real;
        private int restantes = -1;

        UnidadFallible(SagasEnMemoria.Unidad real) { this.real = real; }
        void caerEnLaTransaccion(int n) { restantes = n; }

        @Override
        public <T> T ejecutar(Supplier<T> trabajo) {
            if (real.enTransaccion()) return real.ejecutar(trabajo);       // anidada: es la misma transacción
            boolean cae = restantes > 0 && --restantes == 0;
            return real.ejecutar(() -> {
                T r = trabajo.get();
                if (cae) throw new CaidaDelProceso("antes de confirmar la transacción");   // todo lo hecho se descarta
                return r;
            });
        }
    }

    /** Todo el montaje: el servicio de pedidos hexagonal tal cual, las sagas, tres dobles y un orquestador. Reiniciar = otro orquestador sobre lo mismo. */
    static final class Mundo {
        final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        final SagasEnMemoria sagas = new SagasEnMemoria();
        final SagasEnMemoria.Unidad unidadReal = sagas.unidad(almacen);
        final UnidadFallible unidad = new UnidadFallible(unidadReal);
        final ServicioDePedidos pedidos;
        final Comun comun = new Comun(unidadReal);
        final InventarioDoble inventario;
        final PagosDoble pagos = new PagosDoble(comun);
        final EnviosDoble envios = new EnviosDoble(comun);
        ServicioDeCompras servicio;
        Pagos pagosUsado;
        volatile boolean fallarAlConfirmar;

        Mundo(int existencias) {
            almacen.guardar(Existencias.de("libro", 100_000));
            pedidos = new ServicioDePedidos(almacen, new CatalogoEnMemoria(java.util.Map.of("libro", new Dinero(5000))), almacen, almacen, almacen,
                    new RelojDelSistema(), almacen);
            inventario = new InventarioDoble(comun, existencias);
            pagosUsado = pagos;
            reiniciar();
        }

        void reiniciar() {
            ConfirmarPedido confirmar = id -> {
                VistaDePedido v = pedidos.confirmar(id);
                if (fallarAlConfirmar) { fallarAlConfirmar = false; throw new IllegalStateException("fallo al cerrar el pedido"); }   // DESPUÉS de guardar la saga
                return v;
            };
            servicio = new ServicioDeCompras(pedidos, confirmar, pedidos, pedidos, sagas, unidad, inventario, pagosUsado, envios);
        }

        Estado comprar(String id) { return servicio.confirmar(new Compra(id, "cliente-1", "libro", 1, "Quito")); }

        Estado comprarSeguro(String id) {
            try {
                return comprar(id);
            } catch (CaidaDelProceso c) {
                reiniciar();                                   // el proceso murió; el cliente no recibió respuesta y repite la compra
                return comprarSeguro(id);                      // con la misma clave: idempotente (si ya había pedido y saga, solo las avanza)
            }
        }

        void reanudarHastaEstable() {
            for (int i = 0; i < 20 && !sagas.abiertas().isEmpty(); i++) {
                try { servicio.reanudar(); } catch (CaidaDelProceso c) { reiniciar(); }
            }
        }

        List<VistaDePedido> losPedidos() { return pedidos.deCliente("cliente-1"); }

        /** Paso 9: la invariante de la tienda, comprobada contra los efectos de los participantes. */
        List<String> violaciones() {
            List<String> v = new java.util.ArrayList<>();
            for (VistaDePedido p : losPedidos()) {
                String id = p.id();
                boolean cobroVigente = pagos.cobroVigente(id), reserva = inventario.reservaVigente(id), despacho = envios.despachos.contains(id);
                switch (p.estado()) {
                    case "CONFIRMADO" -> { if (!(cobroVigente && reserva && despacho)) v.add(id + " confirmado sin cobro/reserva/despacho"); }
                    case "CANCELADO" -> { if (cobroVigente || reserva) v.add(id + " cancelado con cobro o reserva vigente"); }
                    default -> { }
                }
                if (cobroVigente && !despacho) v.add(id + " cobrado y no enviado ni reembolsado (puede ser un paso en curso)");
            }
            if (inventario.existencias < 0) v.add("existencias negativas");
            return v;
        }

        String motivoDe(String pedidoId) { return sagas.buscar(pedidoId).orElseThrow().motivo(); }
        Estado estadoDe(String pedidoId) { return sagas.buscar(pedidoId).orElseThrow().estado(); }
        String primerPedido() { return losPedidos().get(0).id(); }
    }

    // ---- Paso 4: el estado de la saga se confirma o se revierte junto con el pedido ----
    @Test
    void sagaYPedidoSeRevierteJuntos() {
        Mundo m = new Mundo(5);
        m.unidad.caerEnLaTransaccion(1);                    // la primera transacción (pedido + saga) muere antes de confirmar
        assertThrows(CaidaDelProceso.class, () -> m.comprar("k1"));
        assertTrue(m.losPedidos().isEmpty(), "no quedó el pedido");
        assertTrue(m.sagas.todas().isEmpty(), "no quedó la saga");
    }

    @Test
    void fallarAlCerrarElPedidoRevierteLaSagaQueYaSeHabiaGuardado() {
        Mundo m = new Mundo(5);
        m.fallarAlConfirmar = true;                         // el fallo llega DESPUÉS de guardar la saga, en la misma transacción
        assertThrows(IllegalStateException.class, () -> m.comprar("k1"));
        String id = m.primerPedido();
        assertEquals(Estado.PROGRAMANDO_ENVIO, m.estadoDe(id), "la saga no avanzó a COMPLETADA");
        assertEquals("PENDIENTE", m.losPedidos().get(0).estado());
        m.reanudarHastaEstable();                           // tras reintentar, se cierra con coherencia
        assertEquals("CONFIRMADO", m.losPedidos().get(0).estado());
        assertEquals(1, m.envios.despachos.size());
    }

    // ---- Paso 6: los cuatro caminos, con las compensaciones y su orden ----
    @Test
    void todoBien() {
        Mundo m = new Mundo(5);
        assertEquals(Estado.COMPLETADA, m.comprar("k1"));
        String id = m.primerPedido();
        assertEquals(List.of("reservar " + id, "cobrar " + id, "programar " + id), m.comun.llamadas);
        assertEquals("CONFIRMADO", m.losPedidos().get(0).estado());
        assertEquals(List.of(), m.violaciones());
    }

    @Test
    void pagoRechazado() {
        Mundo m = new Mundo(5);
        m.pagos.fallos.programar("cobrar", Fallo.RECHAZAR);
        assertEquals(Estado.CANCELADA, m.comprar("k1"));
        String id = m.primerPedido();
        assertEquals(List.of("reservar " + id, "liberar " + id), m.comun.llamadas);    // solo se compensa lo hecho: la reserva
        assertEquals("pago rechazado", m.motivoDe(id));
        assertEquals("CANCELADO", m.losPedidos().get(0).estado());
        assertEquals(List.of(), m.violaciones());
    }

    @Test
    void sinExistencias() {
        Mundo m = new Mundo(0);
        assertEquals(Estado.CANCELADA, m.comprar("k1"));
        assertEquals(List.of(), m.comun.llamadas);                               // nada que compensar: reservar va primero
        assertEquals("sin existencias", m.motivoDe(m.primerPedido()));
    }

    @Test
    void envioImposibleDespuesDeCobrarYReservar() {
        Mundo m = new Mundo(5);
        m.envios.fallos.programar("programar", Fallo.RECHAZAR);
        assertEquals(Estado.CANCELADA, m.comprar("k1"));
        String id = m.primerPedido();
        assertEquals(List.of("reservar " + id, "cobrar " + id, "reembolsar " + id, "liberar " + id), m.comun.llamadas);   // orden inverso
        assertEquals(List.of(), m.violaciones());
    }

    // ---- Paso 7: respuesta perdida en cada participante; jamás se compensa sin saber ----
    @Test
    void respuestaPerdidaNuncaCompensa() {
        for (String[] caso : new String[][] {{"inventario", "reservar"}, {"pagos", "cobrar"}, {"envios", "programar"}}) {
            for (Fallo f : new Fallo[] {Fallo.PERDER_RESPUESTA_TRAS_HACERLO, Fallo.PERDER_RESPUESTA_SIN_HACERLO}) {
                Mundo m = new Mundo(5);
                switch (caso[0]) {
                    case "inventario" -> m.inventario.fallos.programar(caso[1], f);
                    case "pagos" -> m.pagos.fallos.programar(caso[1], f);
                    default -> m.envios.fallos.programar(caso[1], f);
                }
                Estado tras = m.comprar("k1");
                assertTrue(tras.abierta(), caso[0] + "/" + f + ": la saga se queda abierta, no compensa: " + tras);
                assertFalse(m.comun.llamadas.stream().anyMatch(l -> l.startsWith("liberar") || l.startsWith("reembolsar")),
                        "no hubo compensación sin saber");
                m.reanudarHastaEstable();                           // cuando el participante responde, termina bien
                assertEquals(Estado.COMPLETADA, m.estadoDe(m.primerPedido()));
                assertEquals(1, m.pagos.cobrosEjecutados, "un solo cobro aunque se repitiera la orden");
                assertEquals(List.of(), m.violaciones());
            }
        }
    }

    // ---- Paso 8: la saga se retoma tras una caída del proceso en cada punto ----
    @Test
    void reanudaTrasCaidaEnCadaTransaccionLocal() {
        for (int n = 1; n <= 4; n++) {                              // las 4 transacciones del camino feliz
            Mundo m = new Mundo(5);
            m.unidad.caerEnLaTransaccion(n);
            m.comprarSeguro("k1");
            m.reanudarHastaEstable();
            m.reanudarHastaEstable();
            assertEquals(1, m.losPedidos().size(), "caída en la transacción " + n + ": un solo pedido");
            assertEquals(Estado.COMPLETADA, m.estadoDe(m.primerPedido()), "caída en la transacción " + n);
            assertEquals(1, m.pagos.cobrosEjecutados, "caída en la transacción " + n + ": un solo cobro");
            assertEquals(List.of(), m.violaciones());
        }
    }

    @Test
    void reanudaTrasCaidaDuranteLaLlamadaRemotaYEnLaCompensacion() {
        Mundo c = new Mundo(5);
        c.pagos.fallos.programar("cobrar", Fallo.CAER_EL_PROCESO);
        c.comprarSeguro("k1");
        c.reanudarHastaEstable();
        assertEquals(Estado.COMPLETADA, c.estadoDe(c.primerPedido()));
        for (int n = 1; n <= 6; n++) {                              // camino con compensaciones: 6 transacciones
            Mundo m = new Mundo(5);
            m.envios.fallos.programarSiempre("programar", Fallo.RECHAZAR);   // el rechazo es una propiedad del mundo, no de una llamada
            m.unidad.caerEnLaTransaccion(n);
            m.comprarSeguro("k1");
            m.reanudarHastaEstable();
            m.reanudarHastaEstable();
            assertEquals(Estado.CANCELADA, m.estadoDe(m.primerPedido()), "caída en la transacción " + n);
            assertEquals(List.of(), m.violaciones());
        }
    }

    // ---- Paso 9: invariante con fallos mezclados ----
    @Test
    void invarianteConFallosMezclados() {
        Random azar = new Random(42);
        Mundo m = new Mundo(60);
        Fallo[] posibles = Fallo.values();
        for (int i = 0; i < 200; i++) {
            if (azar.nextInt(3) == 0) m.pagos.fallos.programar("cobrar", posibles[azar.nextInt(posibles.length)]);
            if (azar.nextInt(4) == 0) m.envios.fallos.programar("programar", posibles[azar.nextInt(posibles.length)]);
            if (azar.nextInt(4) == 0) m.inventario.fallos.programar("liberar", Fallo.PERDER_RESPUESTA_TRAS_HACERLO);
            if (azar.nextInt(5) == 0) m.unidad.caerEnLaTransaccion(1 + azar.nextInt(4));
            m.comprarSeguro("k" + i);
            if (azar.nextInt(3) == 0) m.reanudarHastaEstable();
        }
        m.reanudarHastaEstable();
        assertTrue(m.sagas.abiertas().isEmpty(), "toda saga terminó");
        assertEquals(List.of(), m.violaciones());
        long confirmados = m.losPedidos().stream().filter(p -> p.estado().equals("CONFIRMADO")).count();
        long cancelados = m.losPedidos().stream().filter(p -> p.estado().equals("CANCELADO")).count();
        assertEquals(200, confirmados + cancelados);
        assertTrue(confirmados > 20 && cancelados > 20, "la mezcla ejercitó los dos finales: " + confirmados + "/" + cancelados);
        assertEquals(m.pagos.cobrados.size(), m.pagos.cobrosEjecutados, "ningún cobro doble");
    }

    // ---- Criterios: el orquestador no importa ninguna tecnología; el servicio de pedidos no cambió ----
    @Test
    void elOrquestadorNoImportaTecnologia() throws IOException {
        Set<String> permitidos = Set.of("tienda.", "java.util.");
        for (String archivo : List.of("aplicacion/saga/ServicioDeCompras.java", "aplicacion/saga/Puertos.java", "dominio/saga/SagaDeCompra.java")) {
            try (Stream<String> lineas = Files.lines(Path.of("src/main/java/tienda", archivo))) {
                lineas.filter(l -> l.startsWith("import ")).forEach(l -> {
                    String destino = l.replace("import ", "").replace("static ", "").replace(";", "").trim();
                    assertTrue(permitidos.stream().anyMatch(destino::startsWith), archivo + " importa " + destino);
                });
            }
        }
    }

    static final Path ORIGEN = Path.of("../../../../../hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src");

    @Test
    void elServicioDePedidosNoSeModifico() throws IOException {
        int iguales = 0;
        try (Stream<Path> todos = Files.walk(ORIGEN)) {
            for (Path o : todos.filter(Files::isRegularFile).collect(Collectors.toList())) {
                Path copia = Path.of("target/hexagonal-base").resolve(ORIGEN.relativize(o));
                assertTrue(Arrays.equals(Files.readAllBytes(o), Files.readAllBytes(copia)), o + " difiere de lo compilado");
                iguales++;
            }
        }
        List<String> nuevos;
        try (Stream<Path> propios = Files.walk(Path.of("src/main/java"))) {
            nuevos = propios.filter(Files::isRegularFile).map(p -> Path.of("src/main/java").relativize(p).toString()).sorted().collect(Collectors.toList());
        }
        for (String n : nuevos) assertFalse(Files.exists(ORIGEN.resolve(Path.of(n))), n + " sustituye a un archivo del servicio de pedidos");
        assertEquals(List.of("tienda/adaptadores/salida/http/PagosHttp.java", "tienda/adaptadores/salida/memoria/SagasEnMemoria.java",
                "tienda/aplicacion/saga/Puertos.java", "tienda/aplicacion/saga/ServicioDeCompras.java", "tienda/dominio/saga/SagaDeCompra.java"), nuevos);
        System.out.println(iguales + " archivos del servicio de pedidos intactos; nuevos: " + nuevos);
    }

    // ---- Paso 10: pagos real por HTTP, sin tocar el orquestador ----
    @Test
    void pagosPorHttpSinTocarElOrquestador() throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Set<String> cobros = new HashSet<>();
        AtomicInteger fallos503 = new AtomicInteger(1);
        Set<String> rechazados = new HashSet<>();
        servidor.createContext("/cobros/", ex -> {
            String id = ex.getRequestURI().getPath().substring("/cobros/".length());
            int codigo;
            if (rechazados.contains(id)) codigo = 402;
            else if (fallos503.getAndDecrement() > 0) codigo = 503;      // la primera vez, caído
            else { cobros.add(id); codigo = 200; }
            ex.sendResponseHeaders(codigo, -1); ex.close();
        });
        servidor.createContext("/reembolsos/", ex -> { ex.sendResponseHeaders(200, -1); ex.close(); });
        servidor.start();
        try {
            Mundo m = new Mundo(5);
            m.pagosUsado = new PagosHttp(URI.create("http://127.0.0.1:" + servidor.getAddress().getPort()));
            m.reiniciar();
            assertTrue(m.comprar("ok-1").abierta(), "503: no se sabe, la saga espera");
            String primero = m.primerPedido();
            m.reanudarHastaEstable();
            assertEquals(Estado.COMPLETADA, m.estadoDe(primero));
            assertEquals(Set.of(primero), cobros);
            rechazados.add("pedido-2");                                        // el siguiente pedido que cree el servicio
            assertEquals(Estado.CANCELADA, m.comprar("rechazado"));          // 402: rechazo definitivo, compensa
        } finally {
            servidor.stop(0);
        }
    }
}
