package bicis;

import java.time.Clock;
import java.time.LocalDateTime;

/** Adaptador real del puerto Reloj. Ninguna prueba lo usa: las pruebas no dependen de la hora real. */
public final class RelojDelSistema implements Reloj {

    private final Clock reloj;

    public RelojDelSistema(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }
}
