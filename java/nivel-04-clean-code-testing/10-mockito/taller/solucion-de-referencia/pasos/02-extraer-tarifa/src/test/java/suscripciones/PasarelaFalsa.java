package suscripciones;

import java.util.ArrayList;
import java.util.List;

/** Fake de Cobros: anota lo que se le pide y puede configurarse para rechazar. */
class PasarelaFalsa implements Cobros {

    private final List<Cobro> cobros = new ArrayList<>();
    private String motivoDeRechazo;

    void rechazarConMotivo(String motivo) {
        this.motivoDeRechazo = motivo;
    }

    List<Cobro> cobrosRecibidos() {
        return List.copyOf(cobros);
    }

    @Override
    public Comprobante cobrar(Cobro cobro) {
        if (motivoDeRechazo != null) {
            throw new PagoRechazadoException(motivoDeRechazo);
        }
        cobros.add(cobro);
        return new Comprobante("FALSO-" + cobros.size());
    }
}
