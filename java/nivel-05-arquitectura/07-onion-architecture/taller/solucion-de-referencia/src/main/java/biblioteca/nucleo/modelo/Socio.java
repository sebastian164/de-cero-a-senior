package biblioteca.nucleo.modelo;

public class Socio {
    private final String id;
    private final TipoSocio tipo;
    private long multasPendientes;

    public Socio(String id, TipoSocio tipo, long multasPendientes) {
        this.id = id;
        this.tipo = tipo;
        this.multasPendientes = multasPendientes;
    }

    public boolean tieneMultas() { return multasPendientes > 0; }
    public void cargarMulta(long pesos) { multasPendientes += pesos; }
    public String id() { return id; }
    public TipoSocio tipo() { return tipo; }
    public long multasPendientes() { return multasPendientes; }
}
