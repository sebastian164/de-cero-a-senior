package biblioteca.nucleo.modelo;

public class Ejemplar {
    private final String codigo;
    private final boolean deReferencia;   // de consulta en sala: no se presta
    private boolean prestado;

    public Ejemplar(String codigo, boolean deReferencia, boolean prestado) {
        this.codigo = codigo;
        this.deReferencia = deReferencia;
        this.prestado = prestado;
    }

    /** Antes estaba como if en el servicio de aplicación; es una regla del ejemplar. */
    public void marcarPrestado() {
        if (deReferencia) throw new PrestamoRechazado("Los ejemplares de referencia no se prestan");
        if (prestado) throw new PrestamoRechazado("El ejemplar ya está prestado");
        prestado = true;
    }

    public void marcarDevuelto() { prestado = false; }
    public String codigo() { return codigo; }
    public boolean prestado() { return prestado; }
}
