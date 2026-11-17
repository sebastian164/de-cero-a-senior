package tienda;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La base de datos del orquestador: lo único, junto con el broker, que sobrevive a una caída.
 * Tiene transacciones de verdad (todo o nada) con una copia de seguridad que se restaura si la acción lanza.
 */
public final class Almacen {

    public static final class Saga {
        public final String id;
        public final long centavos;
        public Estado estado;
        public String destinoPendiente;      // a quién esperamos
        public String ordenPendiente;        // qué orden mandamos (para reenviarla igual)
        public String idOrdenPendiente;
        public int intentos;
        public int ciclosSinRespuesta;
        public long ultimoCambio;            // ciclo de la última transición
        public final List<String> transiciones = new ArrayList<>();

        Saga(String id, long centavos) {
            this.id = id;
            this.centavos = centavos;
        }

        Saga copia() {
            Saga c = new Saga(id, centavos);
            c.estado = estado;
            c.destinoPendiente = destinoPendiente;
            c.ordenPendiente = ordenPendiente;
            c.idOrdenPendiente = idOrdenPendiente;
            c.intentos = intentos;
            c.ciclosSinRespuesta = ciclosSinRespuesta;
            c.ultimoCambio = ultimoCambio;
            c.transiciones.addAll(transiciones);
            return c;
        }
    }

    public record FilaDeSalida(String destino, Mensaje mensaje) {
    }

    public final Map<String, Saga> sagas = new LinkedHashMap<>();
    public final Set<String> procesados = new LinkedHashSet<>();
    public final List<FilaDeSalida> salida = new ArrayList<>();

    public void transaccion(Runnable accion) {
        Map<String, Saga> copiaSagas = new LinkedHashMap<>();
        sagas.forEach((id, saga) -> copiaSagas.put(id, saga.copia()));
        Set<String> copiaProcesados = new LinkedHashSet<>(procesados);
        List<FilaDeSalida> copiaSalida = new ArrayList<>(salida);
        try {
            accion.run();
        } catch (RuntimeException error) {
            sagas.clear();
            sagas.putAll(copiaSagas);
            procesados.clear();
            procesados.addAll(copiaProcesados);
            salida.clear();
            salida.addAll(copiaSalida);
            throw error;
        }
    }

    public Saga nueva(String id, long centavos) {
        Saga saga = new Saga(id, centavos);
        sagas.put(id, saga);
        return saga;
    }
}
