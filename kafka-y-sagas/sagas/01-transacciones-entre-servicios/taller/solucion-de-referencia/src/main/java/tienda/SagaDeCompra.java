package tienda;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import tienda.Participantes.Envios;
import tienda.Participantes.Fidelidad;
import tienda.Participantes.Inventario;
import tienda.Participantes.Notificaciones;
import tienda.Participantes.Pagos;
import tienda.Participantes.Pedidos;
import tienda.Participantes.Rechazo;

/** La saga de "confirmar una compra", en memoria y en un solo proceso, con el patrón del ejemplo. */
public final class SagaDeCompra {

    public enum Tipo { COMPENSABLE, PIVOTE, REINTENTABLE }

    public record Paso(String nombre, Tipo tipo, Runnable accion, Runnable compensacion) {
    }

    public record Compra(String pedido, String cliente, String producto, int unidades, long centavos, String ciudad) {
    }

    public enum Resultado { CONFIRMADA, CANCELADA, INCONSISTENTE }

    public final Pedidos pedidos = new Pedidos();
    public final Inventario inventario = new Inventario();
    public final Pagos pagos = new Pagos();
    public final Envios envios = new Envios();
    public final Fidelidad fidelidad = new Fidelidad();
    public final Notificaciones notificaciones = new Notificaciones();
    public final List<String> traza = new ArrayList<>();
    public boolean conPuntosDeFidelidad;                     // paso 7: el quinto paso

    /** El único sitio que conoce el orden. Añadir un paso es añadir una línea aquí. */
    List<Paso> pasos(Compra c) {
        List<Paso> pasos = new ArrayList<>(List.of(
                new Paso("reservar inventario", Tipo.COMPENSABLE,
                        () -> inventario.reservar(c.pedido(), c.producto(), c.unidades()),
                        () -> inventario.liberar(c.pedido(), c.producto())),
                new Paso("cobrar", Tipo.COMPENSABLE,
                        () -> pagos.cobrar(c.pedido(), c.centavos()),
                        () -> pagos.reembolsar(c.pedido())),
                new Paso("programar envío", Tipo.PIVOTE,
                        () -> envios.programar(c.pedido(), c.ciudad()),
                        null),
                new Paso("confirmar pedido", Tipo.REINTENTABLE,
                        () -> pedidos.confirmar(c.pedido()), null)));
        if (conPuntosDeFidelidad) {
            pasos.add(new Paso("sumar puntos", Tipo.REINTENTABLE,
                    () -> fidelidad.sumar(c.cliente(), c.centavos()), null));
        }
        pasos.add(new Paso("enviar correo", Tipo.REINTENTABLE,
                () -> notificaciones.enviar(c.cliente() + ": tu pedido " + c.pedido() + " está confirmado"), null));
        return pasos;
    }

    public Resultado ejecutar(Compra compra) {
        pedidos.crear(compra.pedido());
        traza.add(compra.pedido() + ": creado PENDIENTE");
        Deque<Paso> completados = new ArrayDeque<>();
        for (Paso paso : pasos(compra)) {
            try {
                paso.accion().run();
                traza.add(compra.pedido() + ": " + paso.nombre());
                completados.push(paso);
            } catch (Rechazo rechazo) {
                if (paso.tipo() == Tipo.REINTENTABLE) {
                    throw new IllegalStateException("un paso reintentable no puede rechazar: " + paso.nombre());
                }
                traza.add(compra.pedido() + ": falló " + paso.nombre() + " (" + rechazo.getMessage() + ")");
                return compensar(compra, completados, rechazo.getMessage());
            }
        }
        return Resultado.CONFIRMADA;
    }

    private Resultado compensar(Compra compra, Deque<Paso> completados, String motivo) {
        while (!completados.isEmpty()) {
            Paso paso = completados.pop();
            try {
                paso.compensacion().run();
                traza.add(compra.pedido() + ": compensado " + paso.nombre());
            } catch (RuntimeException fallo) {
                // Paso 8: no se arregla aún. La saga se detiene aquí y lo que queda por compensar se pierde.
                traza.add(compra.pedido() + ": ¡falló la compensación de " + paso.nombre() + "! " + fallo.getMessage());
                pedidos.estados.put(compra.pedido(), "INCONSISTENTE");
                return Resultado.INCONSISTENTE;
            }
        }
        pedidos.cancelar(compra.pedido(), motivo);
        notificaciones.enviar(compra.cliente() + ": no pudimos completar " + compra.pedido() + " (" + motivo + ")");
        return Resultado.CANCELADA;
    }

    public String estado() {
        return "pedidos=" + pedidos.estados + "\n  existencias=" + inventario.existencias + " reservas=" + inventario.reservas
                + "\n  cobros=" + pagos.cobros + " reembolsos=" + pagos.reembolsos + "\n  despachos=" + envios.despachos
                + " puntos=" + fidelidad.puntos;
    }
}
