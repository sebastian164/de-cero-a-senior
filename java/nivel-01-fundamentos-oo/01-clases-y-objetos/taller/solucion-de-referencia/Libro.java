/** Un libro sabe cuántos ejemplares tiene y protege esa cuenta. */
public class Libro {

    private final String titulo;
    private final String autor;
    private final int ejemplares;
    private int disponibles;

    public Libro(String titulo, String autor, int ejemplares) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El libro necesita título");
        }
        if (autor == null || autor.isBlank()) {
            throw new IllegalArgumentException("El libro necesita autor");
        }
        if (ejemplares < 0) {
            throw new IllegalArgumentException("Los ejemplares no pueden ser negativos");
        }
        this.titulo = titulo;
        this.autor = autor;
        this.ejemplares = ejemplares;
        this.disponibles = ejemplares;
    }

    public void prestar() {
        if (disponibles == 0) {
            throw new IllegalStateException("No hay ejemplares de " + titulo);
        }
        disponibles--;
    }

    /** El error de la versión original: devolver sin haber prestado inflaba el inventario. */
    public void devolver() {
        if (disponibles == ejemplares) {
            throw new IllegalStateException("No hay ejemplares prestados de " + titulo);
        }
        disponibles++;
    }

    public String titulo() { return titulo; }
    public int disponibles() { return disponibles; }

    @Override
    public String toString() {
        return titulo + " (" + autor + "): " + disponibles;
    }
}
