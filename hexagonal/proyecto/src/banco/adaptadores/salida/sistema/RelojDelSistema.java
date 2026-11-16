package banco.adaptadores.salida.sistema;

import banco.aplicacion.puertos.salida.Reloj;
import java.time.Instant;

public final class RelojDelSistema implements Reloj {

    @Override
    public Instant ahora() {
        return Instant.now();
    }
}
