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
        String linea = String.join(";", c.mascota(), c.dueno().nombre(), c.dueno().correo(), c.tipo().nombre(),
                c.fecha().toString(), String.valueOf(c.precio())) + "\n";
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

    private List<Cita> todas() {
        if (!Files.exists(archivo)) return List.of();
        try {
            List<Cita> citas = new ArrayList<>();
            for (String linea : Files.readAllLines(archivo)) {
                String[] p = linea.split(";");
                TipoCita tipo = TiposDeCita.porNombre(p[3]).orElseThrow(() -> new IllegalStateException("Tipo desconocido " + p[3]));
                citas.add(new Cita(p[0], new Dueno(p[1], p[2]), tipo, LocalDateTime.parse(p[4]), Long.parseLong(p[5])));
            }
            return citas;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
