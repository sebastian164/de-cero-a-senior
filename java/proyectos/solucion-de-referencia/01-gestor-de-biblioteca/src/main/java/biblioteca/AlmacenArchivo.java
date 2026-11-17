package biblioteca;

import biblioteca.Biblioteca.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Guarda el estado en un archivo de texto: una línea por registro, campos separados por tabulador. */
public class AlmacenArchivo implements Almacen {
    private final Path archivo;

    public AlmacenArchivo(Path archivo) { this.archivo = archivo; }

    @Override
    public Estado cargar() {
        List<Libro> libros = new ArrayList<>(); List<Socio> socios = new ArrayList<>();
        List<Prestamo> prestamos = new ArrayList<>(); List<Espera> esperas = new ArrayList<>();
        if (Files.exists(archivo)) {
            try {
                for (String linea : Files.readAllLines(archivo)) {
                    String[] c = linea.split("\t");
                    switch (c[0]) {
                        case "L" -> libros.add(new Libro(c[1], c[2], Integer.parseInt(c[3])));
                        case "S" -> socios.add(new Socio(c[1], c[2], Integer.parseInt(c[3])));
                        case "P" -> prestamos.add(new Prestamo(c[1], c[2], LocalDate.parse(c[3])));
                        case "E" -> esperas.add(new Espera(c[1], c[2]));
                        default -> throw new IllegalStateException("Línea desconocida: " + linea);
                    }
                }
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        return new Estado(libros, socios, prestamos, esperas);
    }

    @Override
    public void guardar(Estado e) {
        List<String> l = new ArrayList<>();
        e.libros().forEach(x -> l.add("L\t" + x.isbn() + "\t" + x.titulo() + "\t" + x.ejemplares()));
        e.socios().forEach(x -> l.add("S\t" + x.id() + "\t" + x.nombre() + "\t" + x.multaCentavos()));
        e.prestamos().forEach(x -> l.add("P\t" + x.isbn() + "\t" + x.socioId() + "\t" + x.desde()));
        e.esperas().forEach(x -> l.add("E\t" + x.isbn() + "\t" + x.socioId()));
        try { Files.write(archivo, l); } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
}
