package banco.transferencias.dominio;

public class TransferenciaRechazada extends RuntimeException {
    public TransferenciaRechazada(String motivo) { super(motivo); }
}
