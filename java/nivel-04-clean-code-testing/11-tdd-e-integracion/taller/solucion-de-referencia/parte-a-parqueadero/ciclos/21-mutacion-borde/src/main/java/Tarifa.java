import java.time.Duration;

public final class Tarifa {

    private static final long HORAS_MAXIMAS_COBRADAS_POR_HORA = 12;
    private static final int MINUTOS_POR_HORA = 60;

    public static long precio(TipoVehiculo tipo, Duration permanencia) {
        return precioSinDescuento(tipo, permanencia);
    }

    public static long precioParaResidente(TipoVehiculo tipo, Duration permanencia) {
        return precioSinDescuento(tipo, permanencia) / 2;
    }

    private static long precioSinDescuento(TipoVehiculo tipo, Duration permanencia) {
        if (permanencia.isNegative()) {
            throw new IllegalArgumentException("La permanencia no puede ser negativa: " + permanencia);
        }
        long horas = horasCobradas(permanencia);
        if (horas >= HORAS_MAXIMAS_COBRADAS_POR_HORA) {
            return tipo.tarifaDeDia;
        }
        return tipo.primeraHora + (horas - 1) * tipo.horaAdicional;
    }

    private static long horasCobradas(Duration permanencia) {
        long horasRedondeadasHaciaArriba = (permanencia.toMinutes() + MINUTOS_POR_HORA - 1) / MINUTOS_POR_HORA;
        return Math.max(1, horasRedondeadasHaciaArriba);
    }
}
