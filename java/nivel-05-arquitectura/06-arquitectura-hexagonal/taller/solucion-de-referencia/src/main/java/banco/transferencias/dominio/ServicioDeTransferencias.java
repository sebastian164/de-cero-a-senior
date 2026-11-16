package banco.transferencias.dominio;

/** Regla que no pertenece a una sola cuenta. */
public class ServicioDeTransferencias {
    public void transferir(Cuenta origen, Cuenta destino, Dinero monto, Dinero transferidoHoyDesdeOrigen) {
        if (origen.numero().equals(destino.numero())) throw new TransferenciaRechazada("No se transfiere a la misma cuenta");
        if (monto.esCero()) throw new TransferenciaRechazada("El monto debe ser positivo");
        origen.debitar(monto, transferidoHoyDesdeOrigen);
        destino.acreditar(monto);
    }
}
