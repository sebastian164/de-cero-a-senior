package tienda.adaptadores.salida.sistema;

import java.time.Instant;
import tienda.aplicacion.puertos.salida.Reloj;

public final class RelojDelSistema implements Reloj {

    @Override
    public Instant ahora() {
        return Instant.now();
    }
}
