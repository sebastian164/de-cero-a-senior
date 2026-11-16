package oficina.reservas.dominio;

public class ReglaIncumplida extends RuntimeException {
    public ReglaIncumplida(String mensaje) { super(mensaje); }
}
