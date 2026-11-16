import java.time.Duration;

/** Las reglas de cobro: horas facturables, tarifa por tipo y descuentos. */
public final class CalculadoraCobro {

    private static final int DESCUENTO_RESIDENTE = 50;

    public long cobrar(Vehiculo vehiculo, Duration permanencia) {
        long valor = vehiculo.tipo().tarifa().valorPor(horasFacturables(permanencia));
        if (vehiculo.residente()) {
            valor = valor * (100 - DESCUENTO_RESIDENTE) / 100;
        }
        return valor;
    }

    /** Fracción de hora cuenta como hora completa; mínimo una hora. */
    static long horasFacturables(Duration permanencia) {
        if (permanencia.isNegative()) throw new IllegalArgumentException("La salida es anterior a la entrada");
        long minutos = permanencia.toMinutes() + (permanencia.toSecondsPart() > 0 ? 1 : 0);
        return Math.max(1, (minutos + 59) / 60);
    }
}
