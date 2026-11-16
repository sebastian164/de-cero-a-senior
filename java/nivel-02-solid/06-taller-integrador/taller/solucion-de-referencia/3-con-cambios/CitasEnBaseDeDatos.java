import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Cambio 3: simula una tabla "citas" con id autoincremental y filas planas. */
public final class CitasEnBaseDeDatos implements RepositorioCitas {

    private record Fila(long id, String mascota, String dueno, String correo, String telefono, String canal,
                        String tipo, String fecha, long precio) { }

    private final Map<Long, Fila> tabla = new LinkedHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    @Override
    public void guardar(Cita c) {
        long id = secuencia.incrementAndGet();
        tabla.put(id, new Fila(id, c.mascota(), c.dueno().nombre(), c.dueno().correo(),
                c.dueno().telefono(), c.dueno().canal().name(), c.tipo().nombre(),
                c.fecha().toString(), c.precio()));
    }

    @Override
    public List<Cita> deMascota(String mascota) {
        return seleccionar(f -> f.mascota().equals(mascota));
    }

    @Override
    public List<Cita> delDia(LocalDate dia) {
        return seleccionar(f -> f.fecha().startsWith(dia.toString()));
    }

    @Override
    public List<Cita> deDuenoEnAnio(String correo, int anio) {
        return seleccionar(f -> f.correo().equals(correo) && f.fecha().startsWith(anio + "-"));
    }

    private List<Cita> seleccionar(java.util.function.Predicate<Fila> donde) {
        List<Cita> resultado = new ArrayList<>();
        for (Fila f : tabla.values()) {
            if (donde.test(f)) {
                resultado.add(new Cita(f.mascota(), new Dueno(f.dueno(), f.correo(), f.telefono(), CanalAviso.valueOf(f.canal())),
                        TiposDeCita.porNombre(f.tipo()).orElseThrow(), java.time.LocalDateTime.parse(f.fecha()), f.precio()));
            }
        }
        return resultado;
    }
}
