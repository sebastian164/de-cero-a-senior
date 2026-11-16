/** Cuánto cuesta un número de horas para un tipo de vehículo. */
public record Tarifa(long primeraHora, long horaAdicional, long dia) {

    static final int HORAS_PARA_TARIFA_DE_DIA = 12;
    static final Tarifa GRATIS = new Tarifa(0, 0, 0);

    public long valorPor(long horas) {
        if (horas < 1) throw new IllegalArgumentException("Se cobra al menos una hora");
        if (horas > HORAS_PARA_TARIFA_DE_DIA) {
            long dias = (horas + 23) / 24;           // cada día empezado es un día
            return dias * dia;
        }
        return primeraHora + (horas - 1) * horaAdicional;
    }
}
