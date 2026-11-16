package suscripciones;

import java.time.Clock;
import java.time.LocalDate;

public class ServicioSuscripciones {

    private final Cobros cobros;
    private final RegistroRenovaciones registro;
    private final Clock reloj;

    public ServicioSuscripciones(Cobros cobros, RegistroRenovaciones registro, Clock reloj) {
        this.cobros = cobros;
        this.registro = registro;
        this.reloj = reloj;
    }

    public String renovar(String usuario, int meses) {
        long valor = meses * 30_000L;
        if (meses >= 12) {
            valor = valor * 80 / 100;
        }
        Comprobante comprobante = cobros.cobrar(new Cobro(usuario, valor, "Renovación " + meses + " meses"));
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate vence = hoy.plusMonths(meses);
        registro.registrar(hoy, usuario, valor, comprobante);
        return "Renovado hasta " + vence + " (" + comprobante.codigo() + ")";
    }
}
