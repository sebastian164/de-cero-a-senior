package tienda;

import java.util.Map;
import tienda.Participantes.Envios;
import tienda.Participantes.Inventario;
import tienda.Participantes.Pagos;

public final class Tienda {

    public final Broker broker;
    public final Almacen almacen = new Almacen();
    public final Inventario inventario;
    public final Pagos pagos;
    public final Envios envios;
    public final Orquestador orquestador;
    public final Publicador publicador;
    public long ciclo;

    public Tienda(int existencias, Broker broker) {
        this.broker = broker;
        inventario = new Inventario(broker, existencias);
        pagos = new Pagos(broker);
        envios = new Envios(broker);
        orquestador = new Orquestador(almacen, new Transiciones(false));
        publicador = new Publicador(almacen, broker);
    }

    public Tienda idempotente(boolean si) {
        orquestador.idempotente = si;
        inventario.idempotente = si;
        pagos.idempotente = si;
        envios.idempotente = si;
        return this;
    }

    public Tienda conLapidas(boolean si) {
        inventario.conLapidas = si;
        pagos.conLapidas = si;
        return this;
    }

    public void comprar(String saga, long centavos) {
        pagos.montos.put(saga, centavos);
        orquestador.iniciar(saga, centavos, ciclo);
    }

    public void unCiclo() {
        ciclo++;
        broker.tic();
        publicador.publicarPendientes();
        inventario.atender();
        pagos.atender();
        envios.atender();
        for (Mensaje m; (m = broker.siguiente("orquestador")) != null; ) {
            broker.confirmar("orquestador");
            orquestador.atender(m, ciclo);
        }
        orquestador.tic(ciclo);
    }

    public void correr(int ciclos) {
        for (int i = 0; i < ciclos; i++) {
            unCiclo();
        }
    }

    public Estado estado(String saga) {
        return almacen.sagas.get(saga).estado;
    }

    /**
     * Paso 9, la invariante: toda saga completada está cobrada exactamente una vez, con una reserva de una unidad y
     * un despacho; toda saga cancelada no tiene ni cobro ni reserva; las existencias cuadran.
     */
    public String invariante(int existenciasIniciales) {
        StringBuilder r = new StringBuilder();
        int reservadas = inventario.reservas.values().stream().mapToInt(Integer::intValue).sum();
        if (inventario.existencias + reservadas != existenciasIniciales || inventario.existencias < 0) {
            r.append("existencias descuadradas: ").append(inventario.existencias).append(" + ").append(reservadas).append("; ");
        }
        for (Almacen.Saga s : almacen.sagas.values()) {
            int cobros = pagos.cobros.getOrDefault(s.id, 0);
            int reserva = inventario.reservas.getOrDefault(s.id, 0);
            int despachos = envios.despachos.getOrDefault(s.id, 0);
            switch (s.estado) {
                case COMPLETADA -> {
                    if (cobros != 1 || reserva != 1 || despachos != 1) {
                        r.append(s.id).append(" completada con cobros=").append(cobros).append(" reserva=").append(reserva)
                                .append(" despachos=").append(despachos).append("; ");
                    }
                }
                case CANCELADA -> {
                    if (cobros != 0 || reserva != 0 || despachos != 0) {
                        r.append(s.id).append(" cancelada con cobros=").append(cobros).append(" reserva=").append(reserva)
                                .append(" despachos=").append(despachos).append("; ");
                    }
                }
                default -> r.append(s.id).append(" sin terminar en ").append(s.estado).append("; ");
            }
        }
        return r.toString();
    }

    public Map<String, Estado> estados() {
        Map<String, Estado> m = new java.util.LinkedHashMap<>();
        almacen.sagas.forEach((id, s) -> m.put(id, s.estado));
        return m;
    }
}
