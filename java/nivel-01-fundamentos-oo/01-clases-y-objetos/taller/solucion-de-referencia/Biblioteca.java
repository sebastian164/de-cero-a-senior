import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Cada biblioteca tiene su propio catálogo: no hay estado compartido. */
public class Biblioteca {

    private final String nombre;
    private final List<Libro> libros = new ArrayList<>();

    public Biblioteca(String nombre) {
        this.nombre = nombre;
    }

    public void agregar(Libro libro) {
        if (buscarOpcional(libro.titulo()) != null) {
            throw new IllegalArgumentException("Ya existe " + libro.titulo() + " en " + nombre);
        }
        libros.add(libro);
    }

    public void prestar(String titulo) { buscar(titulo).prestar(); }

    public void devolver(String titulo) { buscar(titulo).devolver(); }

    public int disponibles(String titulo) { return buscar(titulo).disponibles(); }

    public List<Libro> libros() { return Collections.unmodifiableList(libros); }

    public String nombre() { return nombre; }

    private Libro buscar(String titulo) {
        Libro libro = buscarOpcional(titulo);
        if (libro == null) {
            throw new IllegalArgumentException(nombre + " no tiene " + titulo);
        }
        return libro;
    }

    private Libro buscarOpcional(String titulo) {
        for (Libro libro : libros) {
            if (libro.titulo().equals(titulo)) return libro;
        }
        return null;
    }
}
