package nomina;

public class Nomina {

    static String liquidar(long salario, int diasTrabajados, int horasExtra, boolean nocturnas,
                           boolean incluirDetalle, String pais) {
        long base = salario / 30 * diasTrabajados;
        long extra = valorHorasExtra(salario, horasExtra, nocturnas);
        long total = base + extra;
        long descuento = total * porcentajeDeDescuento(pais) / 100;
        String texto = "Neto: " + (total - descuento);
        if (incluirDetalle) {
            texto += " (base " + base + ", extra " + extra + ", descuento " + descuento + ")";
        }
        return texto;
    }

    private static long valorHorasExtra(long salario, int horasExtra, boolean nocturnas) {
        long valorHora = salario / 240;
        int recargo = nocturnas ? 175 : 125;
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
