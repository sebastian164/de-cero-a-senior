import java.time.Duration;

public final class Tarifa {

    public static long precio(TipoVehiculo tipo, Duration permanencia) {
        if (tipo == TipoVehiculo.BICICLETA) {
            return 0;
        }
        long horas = (permanencia.toMinutes() + 59) / 60;
        long primeraHora = tipo == TipoVehiculo.CARRO ? 5_000 : 2_000;
        long horaAdicional = tipo == TipoVehiculo.CARRO ? 3_000 : 1_000;
        return primeraHora + (horas - 1) * horaAdicional;
    }
}
