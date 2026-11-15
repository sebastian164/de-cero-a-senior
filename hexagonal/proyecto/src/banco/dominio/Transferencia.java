package banco.dominio;

import java.time.LocalDate;

/** Mover dinero entre dos cuentas. La regla no es de ninguna de las dos: por eso vive aquí. */
public final class Transferencia {

    private Transferencia() {
    }

    public static void ejecutar(Cuenta origen, Cuenta destino, Dinero valor, LocalDate hoy) {
        if (origen.id().equals(destino.id())) {
            throw new ReglaDeNegocioViolada("no se transfiere a la misma cuenta");
        }
        origen.enviar(valor, hoy);
        destino.depositar(valor);
    }
}
