package tienda;

import tienda.bus.Bus;
import tienda.envios.Envios;
import tienda.fidelidad.Fidelidad;
import tienda.inventario.Inventario;
import tienda.pagos.Pagos;
import tienda.pedidos.Pedidos;

/** La composición: cada servicio se suscribe a lo que le interesa. No hay ninguna clase que conozca el flujo entero. */
public final class Tienda {

    public final Bus bus = new Bus();
    public final Pedidos pedidos;
    public final Inventario inventario;
    public final Pagos pagos;
    public final Envios envios;
    public Fidelidad fidelidad;
    public long ciclo;

    public Tienda(int existencias, boolean cobrarPrimero, boolean conFidelidad) {
        pedidos = new Pedidos(bus, cobrarPrimero);
        inventario = new Inventario(bus, existencias, cobrarPrimero);
        pagos = new Pagos(bus, cobrarPrimero);
        envios = new Envios(bus, cobrarPrimero);
        if (conFidelidad) {
            fidelidad = new Fidelidad(bus);
        }
    }

    public void comprar(String pedido, long centavos) {
        pagos.montos.put(pedido, centavos);
        pedidos.crear(pedido, ciclo);
    }

    public void correr(int ciclos) {
        for (int i = 0; i < ciclos; i++) {
            ciclo++;
            bus.entregar();
        }
    }

    public String inconsistencias() {
        StringBuilder r = new StringBuilder();
        pedidos.estados.forEach((p, estado) -> {
            boolean cobro = pagos.cobros.containsKey(p);
            boolean reserva = inventario.reservas.containsKey(p);
            boolean despacho = envios.despachos.contains(p);
            if (estado.equals("CONFIRMADO") && !(cobro && reserva && despacho)) {
                r.append(p).append(" confirmado incompleto; ");
            }
            if (estado.equals("CANCELADO") && (cobro || reserva || despacho)) {
                r.append(p).append(" cancelado con restos; ");
            }
        });
        return r.toString();
    }

    public String resumen() {
        return "pedidos=" + pedidos.estados + " existencias=" + inventario.existencias + " reservas=" + inventario.reservas.keySet()
                + " cobros=" + pagos.cobros.keySet() + " reembolsos=" + pagos.reembolsos + " despachos=" + envios.despachos
                + (fidelidad == null ? "" : " puntos=" + fidelidad.puntos);
    }
}
