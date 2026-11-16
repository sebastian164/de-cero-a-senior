package nomina;

public class Nomina {

    static String liquidar(long salario, int diasTrabajados, int horasExtra, boolean nocturnas,
                           boolean incluirDetalle, String pais) {
        long base = salario / 30 * diasTrabajados;
        long valorHora = salario / 240;
        long extra;
        if (nocturnas) {
            extra = valorHora * horasExtra * 175 / 100;
        } else {
            extra = valorHora * horasExtra * 125 / 100;
        }
        long total = base + extra;
        long descuento = 0;
        if (pais.equals("CO")) {
            descuento = total * 8 / 100;
        } else if (pais.equals("MX")) {
            descuento = total * 5 / 100;
        }
        String texto = "Neto: " + (total - descuento);
        if (incluirDetalle) {
            texto += " (base " + base + ", extra " + extra + ", descuento " + descuento + ")";
        }
        return texto;
    }
}
