import java.util.List;

/**
 * Solución del taller 02. Ejecutar con: java Main.java
 * Compara la versión original (copiada sin cambios) con la refactorizada en una rejilla de entradas
 * y termina con código 1 si alguna salida difiere.
 */
public class Main {

    public static void main(String[] args) {
        // Uso de la versión nueva: lo que antes era liquidar(3_000_000, 10, 2, true, false, "CO").
        Liquidacion liquidacion = new Liquidador(Pais.deCodigo("CO"))
                .liquidar(3_000_000, new Jornada(10, 2, TipoHoraExtra.NOCTURNA));
        System.out.println(liquidacion.resumen());
        System.out.println(liquidacion.conDetalle());

        int casos = 0;
        int diferencias = 0;
        for (long salario : List.of(0L, 1L, 239L, 1_300_000L, 3_000_000L, 7_777_777L)) {
            for (int dias : List.of(0, 1, 10, 15, 30)) {
                for (int horas : List.of(0, 1, 2, 7, 40)) {
                    for (boolean nocturnas : List.of(true, false)) {
                        for (String pais : List.of("CO", "MX", "PE")) {
                            for (boolean detalle : List.of(true, false)) {
                                casos++;
                                String antes = Original.liquidar(salario, dias, horas, nocturnas, detalle, pais);
                                String despues = nueva(salario, dias, horas, nocturnas, detalle, pais);
                                if (!antes.equals(despues)) {
                                    diferencias++;
                                    System.out.println("DIFERENCIA " + antes + " <> " + despues);
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println(casos + " casos comparados con la versión original, " + diferencias + " diferencias");
        if (diferencias > 0 || casos != 1800) {
            System.exit(1);
        }
    }

    /** Adaptador solo para la comparación: traduce los seis parámetros viejos al modelo nuevo. */
    private static String nueva(long salario, int dias, int horas, boolean nocturnas, boolean detalle, String pais) {
        TipoHoraExtra tipo = nocturnas ? TipoHoraExtra.NOCTURNA : TipoHoraExtra.DIURNA;
        Liquidacion liquidacion = new Liquidador(Pais.deCodigo(pais)).liquidar(salario, new Jornada(dias, horas, tipo));
        return detalle ? liquidacion.conDetalle() : liquidacion.resumen();
    }
}

// ---------------------------------------------------------------- versión refactorizada

enum TipoHoraExtra {
    DIURNA(125), NOCTURNA(175);

    private final int recargoPorcentual;

    TipoHoraExtra(int recargoPorcentual) {
        this.recargoPorcentual = recargoPorcentual;
    }

    long valorDe(long valorHoraOrdinaria) {                         // consulta
        return valorHoraOrdinaria * recargoPorcentual;
    }
}

enum Pais {
    CO(8), MX(5), OTRO(0);

    private final int porcentajeDescuento;

    Pais(int porcentajeDescuento) {
        this.porcentajeDescuento = porcentajeDescuento;
    }

    static Pais deCodigo(String codigo) {                           // consulta
        return switch (codigo) {
            case "CO" -> CO;
            case "MX" -> MX;
            default -> OTRO;
        };
    }

    long descuentoSobre(long total) {                               // consulta
        return total * porcentajeDescuento / 100;
    }
}

/** Lo trabajado en el periodo: reemplaza tres parámetros sueltos y la bandera "nocturnas". */
record Jornada(int diasTrabajados, int horasExtra, TipoHoraExtra tipoHoraExtra) {
}

/** El resultado: reemplaza la bandera "incluirDetalle" por dos consultas con nombre. */
record Liquidacion(long base, long extra, long descuento) {

    long neto() {                                                   // consulta
        return base + extra - descuento;
    }

    String resumen() {                                              // consulta
        return "Neto: " + neto();
    }

    String conDetalle() {                                           // consulta
        return resumen() + " (base " + base + ", extra " + extra + ", descuento " + descuento + ")";
    }
}

class Liquidador {

    private static final int DIAS_DEL_MES = 30;
    private static final int HORAS_DEL_MES = 240;

    private final Pais pais;

    Liquidador(Pais pais) {
        this.pais = pais;
    }

    Liquidacion liquidar(long salario, Jornada jornada) {           // consulta: calcula y devuelve, no modifica nada
        long base = salarioDeLosDias(salario, jornada.diasTrabajados());
        long extra = valorHorasExtra(salario, jornada);
        return new Liquidacion(base, extra, pais.descuentoSobre(base + extra));
    }

    private long salarioDeLosDias(long salario, int dias) {         // consulta
        return salario / DIAS_DEL_MES * dias;
    }

    private long valorHorasExtra(long salario, Jornada jornada) {   // consulta
        long valorHora = salario / HORAS_DEL_MES;
        return jornada.tipoHoraExtra().valorDe(valorHora) * jornada.horasExtra() / 100;
    }
}

// ---------------------------------------------------------------- original, copiado sin cambios del taller/inicio

final class Original {

    private Original() {
    }

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
