package biblioteca.nucleo.modelo;

public enum TipoSocio {
    ESTUDIANTE(3, 15), DOCENTE(8, 30);

    private final int maximoPrestamos;
    private final int diasDePrestamo;
    TipoSocio(int maximoPrestamos, int diasDePrestamo) { this.maximoPrestamos = maximoPrestamos; this.diasDePrestamo = diasDePrestamo; }
    public int maximoPrestamos() { return maximoPrestamos; }
    public int diasDePrestamo() { return diasDePrestamo; }
}
