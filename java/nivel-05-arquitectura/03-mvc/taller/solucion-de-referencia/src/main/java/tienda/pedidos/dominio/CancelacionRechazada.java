package tienda.pedidos.dominio;

public class CancelacionRechazada extends RuntimeException {
    public CancelacionRechazada(String motivo) { super(motivo); }
}
