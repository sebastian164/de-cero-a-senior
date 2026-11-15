package registro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lo mínimo de Kafka: temas con particiones, posiciones, y una posición confirmada por grupo y partición. */
public final class RegistroDeEventos {

    public record Mensaje(int particion, long posicion, String clave, String valor) {
    }

    private final int particiones;
    private final Map<String, List<List<Mensaje>>> temas = new HashMap<>();
    private final Map<String, long[]> confirmadas = new HashMap<>();

    public RegistroDeEventos(int particiones) {
        this.particiones = particiones;
    }

    /** La misma clave va siempre a la misma partición: así se conserva el orden por clave. */
    public synchronized Mensaje publicar(String tema, String clave, String valor) {
        int particion = Math.floorMod(clave.hashCode(), particiones);
        List<Mensaje> registro = tema(tema).get(particion);
        Mensaje mensaje = new Mensaje(particion, registro.size(), clave, valor);
        registro.add(mensaje);
        return mensaje;
    }

    /** Lo que el grupo aún no ha confirmado. Si no confirma, la próxima lectura devuelve lo mismo. */
    public synchronized List<Mensaje> leer(String grupo, String tema) {
        long[] desde = confirmadas(grupo, tema);
        List<Mensaje> pendientes = new ArrayList<>();
        for (int particion = 0; particion < particiones; particion++) {
            List<Mensaje> registro = tema(tema).get(particion);
            pendientes.addAll(registro.subList((int) desde[particion], registro.size()));
        }
        return pendientes;
    }

    public synchronized void confirmar(String grupo, String tema, Mensaje procesado) {
        confirmadas(grupo, tema)[procesado.particion()] = procesado.posicion() + 1;
    }

    private List<List<Mensaje>> tema(String nombre) {
        return temas.computeIfAbsent(nombre, nuevo -> {
            List<List<Mensaje>> registros = new ArrayList<>();
            for (int particion = 0; particion < particiones; particion++) {
                registros.add(new ArrayList<>());
            }
            return registros;
        });
    }

    private long[] confirmadas(String grupo, String tema) {
        return confirmadas.computeIfAbsent(grupo + "@" + tema, nuevo -> new long[particiones]);
    }
}
