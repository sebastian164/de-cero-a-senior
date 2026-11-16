package mensajeria.despacho.adaptadores;

import mensajeria.despacho.aplicacion.puertos.Mensajeros;
import mensajeria.despacho.dominio.Envio;
import mensajeria.despacho.dominio.Mensajero;
import mensajeria.despacho.dominio.Punto;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda una "fila" inmutable por mensajero, como haría una base: cada lectura crea objetos nuevos
 * y guardar compara versiones (el equivalente de UPDATE ... WHERE version = ?).
 */
public class MensajerosEnMemoria implements Mensajeros {
    record Fila(String id, int capacidad, Punto ubicacion, boolean disponible, long version, List<String> activos) { }

    private final Map<String, Fila> filas = new ConcurrentHashMap<>();
    private final EnviosEnMemoria envios;

    public MensajerosEnMemoria(EnviosEnMemoria envios) { this.envios = envios; }

    public void alta(String id, int capacidad, Punto ubicacion) {
        filas.put(id, new Fila(id, capacidad, ubicacion, true, 0, List.of()));
    }

    private Mensajero cargar(Fila f) {
        List<Envio> activos = f.activos().stream().map(envios::buscar).flatMap(Optional::stream).toList();
        return Mensajero.reconstituir(f.id(), f.capacidad(), f.ubicacion(), f.disponible(), f.version(), activos);
    }

    @Override public Optional<Mensajero> buscar(String id) { return Optional.ofNullable(filas.get(id)).map(this::cargar); }

    @Override
    public List<Mensajero> cercanosA(Punto punto, double radioKm) {
        return filas.values().stream().filter(f -> f.ubicacion().distanciaA(punto) <= radioKm).map(this::cargar).toList();
    }

    @Override
    public void guardar(Mensajero m) {
        List<String> activos = m.activos().stream().map(Envio::id).toList();
        Fila resultado = filas.compute(m.id(), (id, actual) -> {
            if (actual == null || actual.version() != m.version()) throw new ConflictoDeVersion(id);
            return new Fila(id, m.capacidadLitros(), m.ubicacion(), m.disponible(), actual.version() + 1, activos);
        });
        m.versionGuardada(resultado.version());
    }
}
