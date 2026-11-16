package mensajeria.despacho.dominio;

import java.util.ArrayList;
import java.util.List;

/** Agregado envío: protege las transiciones de estado. */
public class Envio {
    private final String id;
    private final String clienteId;
    private final Punto origen;
    private final Punto destino;
    private final Tamano tamano;
    private final Franja franja;
    private final long precio;
    private EstadoEnvio estado = EstadoEnvio.SOLICITADO;
    private String mensajeroId;
    private final List<EstadoEnvio> historial = new ArrayList<>(List.of(EstadoEnvio.SOLICITADO));

    public Envio(String id, String clienteId, Punto origen, Punto destino, Tamano tamano, Franja franja, long precio) {
        this.id = id; this.clienteId = clienteId; this.origen = origen; this.destino = destino;
        this.tamano = tamano; this.franja = franja; this.precio = precio;
    }

    /** Copia para adaptadores que simulan una base (cada lectura es un objeto nuevo). */
    public Envio copia() {
        Envio c = new Envio(id, clienteId, origen, destino, tamano, franja, precio);
        c.estado = estado;
        c.mensajeroId = mensajeroId;
        c.historial.clear();
        c.historial.addAll(historial);
        return c;
    }

    private void pasarA(EstadoEnvio nuevo) {
        if (!estado.siguientes().contains(nuevo)) throw new ReglaDeDespacho("No se pasa de " + estado + " a " + nuevo);
        estado = nuevo;
        historial.add(nuevo);
    }

    void asignarA(String mensajero) { pasarA(EstadoEnvio.ASIGNADO); mensajeroId = mensajero; }
    void liberar() { pasarA(EstadoEnvio.SOLICITADO); mensajeroId = null; }
    public void recoger() { pasarA(EstadoEnvio.RECOGIDO); }
    public void salirARuta() { pasarA(EstadoEnvio.EN_RUTA); }
    public void entregar() { pasarA(EstadoEnvio.ENTREGADO); }
    public void fallar() { pasarA(EstadoEnvio.FALLIDO); }
    public void cancelar() { pasarA(EstadoEnvio.CANCELADO); }

    public boolean activo() { return estado == EstadoEnvio.ASIGNADO || estado == EstadoEnvio.RECOGIDO || estado == EstadoEnvio.EN_RUTA; }
    public String id() { return id; }
    public String clienteId() { return clienteId; }
    public Punto origen() { return origen; }
    public Punto destino() { return destino; }
    public Tamano tamano() { return tamano; }
    public Franja franja() { return franja; }
    public long precio() { return precio; }
    public EstadoEnvio estado() { return estado; }
    public String mensajeroId() { return mensajeroId; }
    public List<EstadoEnvio> historial() { return List.copyOf(historial); }
}
