import java.time.Duration;

public final class Tarifa {

    public static long precio(TipoVehiculo tipo, Duration permanencia) {
        if (tipo == TipoVehiculo.BICICLETA) {
            return 0;
        }
        return 5_000;
    }
}
