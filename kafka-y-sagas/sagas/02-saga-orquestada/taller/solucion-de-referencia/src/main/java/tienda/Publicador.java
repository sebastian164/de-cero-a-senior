package tienda;

import java.util.Iterator;
import tienda.Almacen.FilaDeSalida;

/** Lleva la bandeja de salida al broker. Borra cada fila solo después de enviarla: al menos una vez. */
public final class Publicador {

    private final Almacen almacen;
    private final Broker broker;
    public boolean caerTrasPublicarUna;

    public Publicador(Almacen almacen, Broker broker) {
        this.almacen = almacen;
        this.broker = broker;
    }

    public void publicarPendientes() {
        for (Iterator<FilaDeSalida> filas = almacen.salida.iterator(); filas.hasNext(); ) {
            FilaDeSalida fila = filas.next();
            broker.enviar(fila.destino(), fila.mensaje());
            if (caerTrasPublicarUna) {
                caerTrasPublicarUna = false;
                throw new Orquestador.CaidaSimulada("publicador tras publicar y antes de marcar");
            }
            filas.remove();
        }
    }
}
