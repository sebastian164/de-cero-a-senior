package tienda.integrador.plataforma;

import java.util.concurrent.atomic.AtomicLong;

/** Puntos de fallo programables para las pruebas de caídas: el proceso muere de golpe (halt), sin cerrar nada. */
public final class Muerte {
    private static final AtomicLong PROCESADOS = new AtomicLong();

    private Muerte() {
    }

    /** Tras procesar el n-ésimo mensaje y antes de confirmar su posición. 0 = nunca. */
    public static void siToca(long tras) {
        if (tras > 0 && PROCESADOS.incrementAndGet() == tras) Runtime.getRuntime().halt(137);
    }
}
