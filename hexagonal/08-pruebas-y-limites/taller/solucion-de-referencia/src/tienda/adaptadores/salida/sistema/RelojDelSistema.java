package tienda.adaptadores.salida.sistema;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import tienda.aplicacion.puertos.salida.Reloj;

public final class RelojDelSistema implements Reloj {

    @Override
    public Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);    // la precisión que promete el contrato del repositorio
    }
}
