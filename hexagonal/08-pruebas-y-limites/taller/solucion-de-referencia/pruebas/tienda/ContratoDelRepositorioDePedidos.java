package tienda;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.puertos.salida.RepositorioDePedidos;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.dominio.Dinero;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.LineaDePedido;
import tienda.dominio.Pedido;

/** Lo que cualquier adaptador de RepositorioDePedidos debe cumplir. Se escribe una vez y se ejecuta contra cada uno. */
public final class ContratoDelRepositorioDePedidos {

    /** El repositorio y la unidad de trabajo que lo acompaña: la versión se comprueba entre transacciones. */
    public record Escenario(RepositorioDePedidos pedidos, UnidadDeTrabajo unidadDeTrabajo) {
    }

    /** PostgreSQL guarda microsegundos (y redondea lo que sobra); la memoria guardaría nanosegundos. El contrato promete microsegundos. */
    private static final Instant CON_MICROS = Instant.parse("2026-11-15T10:00:00.123456Z");

    private ContratoDelRepositorioDePedidos() {
    }

    public static List<String> incumplimientos(Supplier<Escenario> fabrica) {
        List<String> incumplidas = new ArrayList<>();
        Escenario escenario = fabrica.get();
        RepositorioDePedidos pedidos = escenario.pedidos();
        UnidadDeTrabajo transaccion = escenario.unidadDeTrabajo();
        String cliente = "contrato-" + UUID.randomUUID();

        String id = pedidos.siguienteId();
        Pedido original = Pedido.crear(id, cliente, List.of(new LineaDePedido("teclado", 2, new Dinero(15_000_000)),
                new LineaDePedido("raton", 1, new Dinero(6_000_000))), CON_MICROS);
        enTransaccion(transaccion, () -> pedidos.guardar(original));

        Pedido leido = transaccion.ejecutar(() -> pedidos.buscar(id)).orElse(null);
        if (leido == null || !leido.cliente().equals(cliente) || leido.estado() != EstadoDePedido.PENDIENTE
                || !leido.lineas().equals(original.lineas())) {
            incumplidas.add("lo guardado se recupera igual, con sus líneas en orden");
            return incumplidas;
        }
        if (!leido.creadoEn().equals(CON_MICROS)) {
            incumplidas.add("la fecha se recupera exacta, al microsegundo");
        }
        if (transaccion.ejecutar(() -> pedidos.buscar("no-existe-" + UUID.randomUUID())).isPresent()) {
            incumplidas.add("buscar algo que no existe devuelve vacío");
        }
        if (pedidos.siguienteId().equals(id)) {
            incumplidas.add("los identificadores no se repiten");
        }

        leido.confirmar();                                               // se modifica y NO se guarda
        if (transaccion.ejecutar(() -> pedidos.buscar(id)).orElseThrow().estado() != EstadoDePedido.PENDIENTE) {
            incumplidas.add("lo que se entrega es una copia");
        }

        String otroId = pedidos.siguienteId();
        enTransaccion(transaccion, () -> pedidos.guardar(Pedido.crear(otroId, cliente, List.of(new LineaDePedido("monitor", 1, new Dinero(1))),
                CON_MICROS.plusSeconds(60))));
        enTransaccion(transaccion, () -> pedidos.guardar(Pedido.crear(pedidos.siguienteId(), cliente + "-otro",
                List.of(new LineaDePedido("monitor", 1, new Dinero(1))), CON_MICROS)));
        List<String> delCliente = transaccion.ejecutar(() -> pedidos.deCliente(cliente)).stream().map(Pedido::id).toList();
        if (!delCliente.equals(List.of(id, otroId))) {
            incumplidas.add("los pedidos de un cliente son solo los suyos, del más antiguo al más reciente");
        }

        try {
            enTransaccion(transaccion, () -> {
                pedidos.guardar(Pedido.existente(id, cliente, original.lineas(), EstadoDePedido.CANCELADO, CON_MICROS));
                throw new IllegalStateException("se revierte");
            });
        } catch (IllegalStateException esperado) {
            // la unidad de trabajo revirtió
        }
        if (transaccion.ejecutar(() -> pedidos.buscar(id)).orElseThrow().estado() != EstadoDePedido.PENDIENTE) {
            incumplidas.add("lo guardado en una transacción revertida no queda");
        }

        try {
            enTransaccion(transaccion, () -> {
                Pedido mio = pedidos.buscar(id).orElseThrow();
                enOtroHilo(() -> enTransaccion(transaccion, () -> {      // otra transacción se adelanta y confirma
                    Pedido suyo = pedidos.buscar(id).orElseThrow();
                    suyo.confirmar();
                    pedidos.guardar(suyo);
                }));
                mio.cancelar();
                pedidos.guardar(mio);                                    // lo leído ya es viejo
            });
            incumplidas.add("guardar lo que se leyó antes de que otro lo cambiara falla con conflicto");
        } catch (ConflictoDeConcurrencia esperado) {
            // cumple
        }
        return incumplidas;
    }

    static void enTransaccion(UnidadDeTrabajo transaccion, Runnable trabajo) {
        transaccion.ejecutar(() -> {
            trabajo.run();
            return null;
        });
    }

    static void enOtroHilo(Runnable accion) {
        RuntimeException[] fallo = new RuntimeException[1];
        Thread otro = new Thread(() -> {
            try {
                accion.run();
            } catch (RuntimeException error) {
                fallo[0] = error;
            }
        }, "el-otro");
        otro.start();
        try {
            otro.join();
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
        if (fallo[0] != null) {
            throw new IllegalStateException("la otra transacción falló", fallo[0]);
        }
    }
}
