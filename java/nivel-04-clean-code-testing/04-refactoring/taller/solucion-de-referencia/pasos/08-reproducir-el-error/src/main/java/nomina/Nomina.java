package nomina;

public class Nomina {

    private static final int DIAS_DEL_MES = 30;
    private static final int HORAS_DEL_MES = 240;
    private static final int RECARGO_NOCTURNO = 175;
    private static final int RECARGO_DIURNO = 125;

    static String liquidar(long salario, int diasTrabajados, int horasExtra, boolean nocturnas,
                           boolean incluirDetalle, String pais) {
        Liquidacion liquidacion = calcular(salario, diasTrabajados, horasExtra, nocturnas, pais);
        return incluirDetalle ? liquidacion.conDetalle() : liquidacion.resumen();
    }

    static Liquidacion calcular(long salario, int diasTrabajados, int horasExtra, boolean nocturnas, String pais) {
        long base = salario / DIAS_DEL_MES * diasTrabajados;
        long extra = valorHorasExtra(salario, horasExtra, nocturnas);
        long descuento = (base + extra) * porcentajeDeDescuento(pais) / 100;
        return new Liquidacion(base, extra, descuento);
    }

    private static long valorHorasExtra(long salario, int horasExtra, boolean nocturnas) {
        long valorHora = salario / HORAS_DEL_MES;
        int recargo = nocturnas ? RECARGO_NOCTURNO : RECARGO_DIURNO;
        return valorHora * horasExtra * recargo / 100;
    }

    private static int porcentajeDeDescuento(String pais) {
        return switch (pais) {
            case "CO" -> 8;
            case "MX" -> 5;
            default -> 0;
        };
    }
}
