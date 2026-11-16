package taller.strategy;

import java.util.Map;

public class Main {

    public static void main(String[] args) {
        HorasExtra horas = new HorasExtra(6, 4);           // 6 diurnas, 4 nocturnas
        long valorHora = 20_000;
        Liquidador liquidador = new Liquidador(Politicas.REGISTRO);
        for (String pais : new String[] {"CO", "MX", "ES"}) {
            System.out.println(pais + " condicional: " + LiquidadorCondicional.liquidar(pais, horas, valorHora)
                    + " | strategy: " + liquidador.liquidar(pais, horas, valorHora));
        }
        System.out.println("CL (cuarta política, solo en Strategy): " + liquidador.liquidar("CL", horas, valorHora));
    }
}

record HorasExtra(int diurnas, int nocturnas) {
    int total() { return diurnas + nocturnas; }
}

/** Paso 2: el resultado puede ser dinero o tiempo. Un tipo sellado obliga a tratar ambos casos. */
sealed interface Liquidacion permits Liquidacion.Pago, Liquidacion.Compensacion {
    record Pago(long pesos) implements Liquidacion {
        @Override public String toString() { return "pagar $" + pesos; }
    }
    record Compensacion(int horasLibres) implements Liquidacion {
        @Override public String toString() { return "compensar " + horasLibres + " h libres"; }
    }
}

// ===================== Paso 1: con condicionales =====================

final class LiquidadorCondicional {
    private LiquidadorCondicional() { }

    static Liquidacion liquidar(String pais, HorasExtra h, long valorHora) {
        if (pais.equals("CO")) {
            return new Liquidacion.Pago(h.total() * valorHora * 125 / 100);
        } else if (pais.equals("MX")) {
            return new Liquidacion.Pago(h.diurnas() * valorHora * 125 / 100 + h.nocturnas() * valorHora * 175 / 100);
        } else if (pais.equals("ES")) {
            return new Liquidacion.Compensacion(h.total());
        }
        throw new IllegalArgumentException("País sin política: " + pais);
    }
}

// ===================== Paso 2: Strategy =====================

@FunctionalInterface
interface PoliticaHorasExtra {
    Liquidacion liquidar(HorasExtra horas, long valorHora);
}

/** Puede ser lambda: una fórmula sin estado ni configuración. */
final class Politicas {
    private Politicas() { }

    static final PoliticaHorasExtra RECARGO_FIJO_25 = (h, v) -> new Liquidacion.Pago(h.total() * v * 125 / 100);

    static final PoliticaHorasExtra DIURNO_25_NOCTURNO_75 =
            (h, v) -> new Liquidacion.Pago(h.diurnas() * v * 125 / 100 + h.nocturnas() * v * 175 / 100);

    static final PoliticaHorasExtra COMPENSACION_EN_TIEMPO = (h, v) -> new Liquidacion.Compensacion(h.total());

    /** Paso 5: la decisión de qué estrategia usa cada filial vive aquí (configuración), no en el liquidador. */
    static final Map<String, PoliticaHorasExtra> REGISTRO = Map.of(
            "CO", RECARGO_FIJO_25,
            "MX", DIURNO_25_NOCTURNO_75,
            "ES", COMPENSACION_EN_TIEMPO,
            "CL", new RecargoConTope(50, 10));    // paso 3: agregada sin tocar Liquidador
}

/**
 * Paso 3: cuarta política. Recargo del 50 % hasta un tope de horas por periodo;
 * las horas que pasan del tope se pagan sin recargo. Tiene configuración
 * (porcentaje y tope) y un nombre de negocio: mejor como clase que como lambda.
 */
final class RecargoConTope implements PoliticaHorasExtra {
    private final int porcentaje;
    private final int topeHoras;

    RecargoConTope(int porcentaje, int topeHoras) {
        if (topeHoras <= 0) throw new IllegalArgumentException("El tope debe ser positivo");
        this.porcentaje = porcentaje;
        this.topeHoras = topeHoras;
    }

    @Override
    public Liquidacion liquidar(HorasExtra h, long valorHora) {
        int conRecargo = Math.min(h.total(), topeHoras);
        int sinRecargo = h.total() - conRecargo;
        return new Liquidacion.Pago(conRecargo * valorHora * (100 + porcentaje) / 100 + sinRecargo * valorHora);
    }
}

/** El contexto: cerrado a modificaciones. */
final class Liquidador {
    private final Map<String, PoliticaHorasExtra> politicas;

    Liquidador(Map<String, PoliticaHorasExtra> politicas) { this.politicas = Map.copyOf(politicas); }

    Liquidacion liquidar(String pais, HorasExtra horas, long valorHora) {
        PoliticaHorasExtra p = politicas.get(pais);
        if (p == null) throw new IllegalArgumentException("País sin política: " + pais);
        return p.liquidar(horas, valorHora);
    }
}
