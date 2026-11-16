import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Adaptador: guarda las citas como líneas de texto. */
public final class CitasEnArchivo implements RepositorioCitas {

    private final Path archivo;

    public CitasEnArchivo(Path archivo) {
        this.archivo = archivo;
    }

    @Override
    public void guardar(Cita c) {
        // Cambio 2: el dueño viaja dentro de la cita, así que su teléfono y su canal también se guardan aquí.
        Dueno d = c.dueno();
        String linea = String.join(";", c.mascota(), d.nombre(), d.correo(), c.tipo().nombre(),
                c.fecha().toString(), String.valueOf(c.precio()),
                d.telefono() == null ? "" : d.telefono(), d.canal().name()) + "\n";
        try {
            Files.writeString(archivo, linea, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<Cita> deMascota(String mascota) {
        return todas().stream().filter(c -> c.mascota().equals(mascota)).toList();
    }

    @Override
    public List<Cita> delDia(LocalDate dia) {
        return todas().stream().filter(c -> c.fecha().toLocalDate().equals(dia)).toList();
    }

    @Override
    public List<Cita> deDuenoEnAnio(String correo, int anio) {
        return todas().stream().filter(c -> c.dueno().correo().equals(correo) && c.fecha().getYear() == anio).toList();
    }

    private List<Cita> todas() {
        if (!Files.exists(archivo)) return List.of();
        try {
            List<Cita> citas = new ArrayList<>();
            for (String linea : Files.readAllLines(archivo)) {
                String[] p = linea.split(";", -1);
                TipoCita tipo = TiposDeCita.porNombre(p[3]).orElseThrow(() -> new IllegalStateException("Tipo desconocido " + p[3]));
                // Las líneas escritas antes del cambio 2 tienen 6 campos: esos dueños reciben correo.
                Dueno dueno = p.length < 8 ? new Dueno(p[1], p[2])
                        : new Dueno(p[1], p[2], p[6].isEmpty() ? null : p[6], CanalAviso.valueOf(p[7]));
                citas.add(new Cita(p[0], dueno, tipo, LocalDateTime.parse(p[4]), Long.parseLong(p[5])));
            }
            return citas;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
