package tienda;

import tienda.Participantes.Envios;
import tienda.Participantes.Fidelidad;
import tienda.Participantes.Inventario;
import tienda.Participantes.Pagos;

/** Todo el sistema en un bucle de ciclos. El orquestador puede morir y ser sustituido por otro en cualquier ciclo. */
public final class Tienda {

    public final Broker broker = new Broker();
    public final Almacen almacen = new Almacen();
    public final Inventario inventario;
    public final Pagos pagos = new Pagos(broker);
    public final Envios envios = new Envios(broker);
    public final Fidelidad fidelidad = new Fidelidad(broker);
    public final Transiciones transiciones;
    public final boolean conSalida;
    public final Publicador publicador;
    public Orquestador orquestador;
    public int caidas;
    public long ciclo;
    public boolean relojActivo = true;

    public Tienda(int existencias, boolean conSalida, boolean conPuntos) {
        this.inventario = new Inventario(broker, existencias);
        this.transiciones = new Transiciones(conPuntos);
        this.conSalida = conSalida;
        this.publicador = new Publicador(almacen, broker);
        this.orquestador = nuevoOrquestador();
    }

    private Orquestador nuevoOrquestador() {
        return new Orquestador(almacen, broker, transiciones, conSalida);
    }

    /** Un proceso nuevo: no hereda nada del anterior salvo el almacén y el broker. */
    public void reiniciarOrquestador() {
        caidas++;
        orquestador = nuevoOrquestador();
    }

    public void comprar(String saga, long centavos) {
        pagos.montos.put(saga, centavos);
        try {
            orquestador.iniciar(saga, centavos, ciclo);
        } catch (Orquestador.CaidaSimulada caida) {
            reiniciarOrquestador();
        }
    }

    public void unCiclo() {
        ciclo++;
        try {
            publicador.publicarPendientes();
        } catch (Orquestador.CaidaSimulada caida) {
            // muere el publicador; el siguiente ciclo es otro proceso
        }
        inventario.atender();
        pagos.atender();
        envios.atender();
        fidelidad.atender();
        try {
            while (orquestador.atenderSiguiente(ciclo)) {
                // atiende todo lo que haya
            }
            if (relojActivo) {
                orquestador.tic(ciclo);
            }
        } catch (Orquestador.CaidaSimulada caida) {
            reiniciarOrquestador();
        }
    }

    public void correr(int ciclos) {
        for (int i = 0; i < ciclos; i++) {
            unCiclo();
        }
    }

    public boolean todasTerminadas() {
        return almacen.sagas.values().stream().allMatch(s -> s.estado.terminal());
    }

    public Estado estado(String saga) {
        return almacen.sagas.get(saga).estado;
    }

    /** Consistencia: completada ⇒ cobro, reserva y despacho; cancelada ⇒ ni cobro, ni reserva, ni despacho. */
    public String inconsistencias() {
        StringBuilder r = new StringBuilder();
        for (Almacen.Saga s : almacen.sagas.values()) {
            boolean cobro = pagos.cobros.containsKey(s.id);
            boolean reserva = inventario.reservas.containsKey(s.id);
            boolean despacho = envios.despachos.contains(s.id);
            if (s.estado == Estado.COMPLETADA && !(cobro && reserva && despacho)) {
                r.append(s.id).append(" completada sin todo; ");
            }
            if (s.estado == Estado.CANCELADA && (cobro || reserva || despacho)) {
                r.append(s.id).append(" cancelada con restos; ");
            }
        }
        return r.toString();
    }

    public String resumen() {
        return "existencias=" + inventario.existencias + " reservas=" + inventario.reservas.keySet()
                + " cobros=" + pagos.cobros.keySet() + " reembolsos=" + pagos.reembolsos
                + " despachos=" + envios.despachos + " puntos=" + fidelidad.puntos;
    }
}
