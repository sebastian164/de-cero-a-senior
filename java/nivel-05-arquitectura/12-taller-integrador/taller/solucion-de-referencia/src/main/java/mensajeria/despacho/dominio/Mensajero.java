package mensajeria.despacho.dominio;

import java.util.ArrayList;
import java.util.List;

/**
 * Agregado mensajero: protege que sus envíos activos sean compatibles.
 * Compatibles = la suma de volúmenes con franja solapada cabe en su capacidad y no supera MAXIMO_SIMULTANEOS.
 * Lleva versión para el bloqueo optimista.
 */
public class Mensajero {
    public static final int MAXIMO_SIMULTANEOS = 3;

    private final String id;
    private final int capacidadLitros;
    private Punto ubicacion;
    private boolean disponible;
    private final List<Envio> activos = new ArrayList<>();
    private long version;

    public Mensajero(String id, int capacidadLitros, Punto ubicacion, boolean disponible, long version) {
        this.id = id; this.capacidadLitros = capacidadLitros; this.ubicacion = ubicacion;
        this.disponible = disponible; this.version = version;
    }

    /** Reconstrucción desde persistencia: los activos ya fueron validados al asignarlos. */
    public static Mensajero reconstituir(String id, int capacidadLitros, Punto ubicacion, boolean disponible,
                                         long version, List<Envio> activos) {
        Mensajero m = new Mensajero(id, capacidadLitros, ubicacion, disponible, version);
        m.activos.addAll(activos);
        return m;
    }

    public int capacidadLitros() { return capacidadLitros; }
    public boolean disponible() { return disponible; }

    public boolean puedeTomar(Envio envio) {
        if (!disponible) return false;
        List<Envio> solapados = activos.stream().filter(e -> e.franja().seSolapaCon(envio.franja())).toList();
        int volumen = solapados.stream().mapToInt(e -> e.tamano().litros()).sum() + envio.tamano().litros();
        return solapados.size() < MAXIMO_SIMULTANEOS && volumen <= capacidadLitros;
    }

    public void asignar(Envio envio) {
        if (!puedeTomar(envio)) throw new ReglaDeDespacho("Asignación incompatible para " + id);
        envio.asignarA(id);
        activos.add(envio);
    }

    public void rechazar(Envio envio) {
        if (!activos.remove(envio)) throw new ReglaDeDespacho("El envío no estaba asignado a " + id);
        envio.liberar();
    }

    public void cerrar(Envio envio) { activos.remove(envio); }
    public void moverA(Punto p) { ubicacion = p; }

    public String id() { return id; }
    public Punto ubicacion() { return ubicacion; }
    public long version() { return version; }
    public void versionGuardada(long nueva) { version = nueva; }
    public List<Envio> activos() { return List.copyOf(activos); }
}
