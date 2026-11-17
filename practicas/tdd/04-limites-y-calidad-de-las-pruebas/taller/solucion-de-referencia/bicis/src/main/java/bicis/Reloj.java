package bicis;

import java.time.LocalDateTime;

/** Puerto: la hora de la devolución. Apareció porque "se devuelve a las 10:50" no puede depender de la hora real. */
public interface Reloj {

    LocalDateTime ahora();
}
