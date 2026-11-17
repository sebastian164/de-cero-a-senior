package bicis;

import java.time.Duration;

/** El precio de un alquiler. Funciones puras: se prueban sin dobles y sin levantar nada. */
public final class Tarifa {

    static final Duration GRATIS = Duration.ofMinutes(30);
    static final Duration BLOQUE = Duration.ofMinutes(15);
    static final int PRECIO_POR_BLOQUE = 1000;
    static final int TOPE_DIARIO = 12_000;
    static final Duration GRATIS_ABONADO = Duration.ofMinutes(60);
    static final int RECARGO_OTRA_ESTACION = 2000;

    private Tarifa() {
    }

    public static int precio(Duration duracion) {
        return precio(duracion, false);
    }

    public static int precio(Duration duracion, boolean abonado) {
        Duration gratis = abonado ? GRATIS_ABONADO : GRATIS;
        return Math.min(bloquesIniciados(duracion.minus(gratis)) * PRECIO_POR_BLOQUE, TOPE_DIARIO * diasIniciados(duracion));
    }

    public static int recargo(String estacionOrigen, String estacionDestino) {
        return estacionOrigen.equals(estacionDestino) ? 0 : RECARGO_OTRA_ESTACION;
    }

    /** Bloques de 15 minutos iniciados en el tiempo cobrable. Un segundo de más inicia un bloque. */
    static int bloquesIniciados(Duration cobrable) {
        if (cobrable.isNegative() || cobrable.isZero()) {
            return 0;
        }
        long porBloque = BLOQUE.toSeconds();
        return (int) ((cobrable.toSeconds() + porBloque - 1) / porBloque);
    }

    static int diasIniciados(Duration duracion) {
        long dia = Duration.ofDays(1).toSeconds();
        return (int) Math.max(1, (duracion.toSeconds() + dia - 1) / dia);
    }
}
