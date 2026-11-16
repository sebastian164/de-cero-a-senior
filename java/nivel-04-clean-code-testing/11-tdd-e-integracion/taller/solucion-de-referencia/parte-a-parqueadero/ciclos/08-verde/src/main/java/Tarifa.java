import java.time.Duration;

public final class Tarifa {

    public static long precio(TipoVehiculo tipo, Duration permanencia) {
        if (tipo == TipoVehiculo.BICICLETA) {
            return 0;
        }
        long horas = (permanencia.toMinutes() + 59) / 60;
        return 5_000 + (horas - 1) * 3_000;
    }
}
