package calculo;

import java.util.SplittableRandom;

/** Disponibilidad y latencia de una cadena síncrona de servicios. */
public final class CadenaDeServicios {
    private CadenaDeServicios() { }

    /** Todos deben responder: las disponibilidades se multiplican. */
    public static double disponibilidad(double cadaUno, int servicios) {
        return Math.pow(cadaUno, servicios);
    }

    /** En serie, las latencias se suman. */
    public static long latenciaMs(long cadaUno, int servicios) {
        return cadaUno * servicios;
    }

    public static double horasCaidoPorMes(double disponibilidad) {
        return (1 - disponibilidad) * 30 * 24;
    }

    /** Simulación: fracción de peticiones que atraviesan toda la cadena sin fallo. */
    public static double simular(double cadaUno, int servicios, int peticiones, long semilla) {
        SplittableRandom azar = new SplittableRandom(semilla);
        int exitosas = 0;
        for (int i = 0; i < peticiones; i++) {
            boolean ok = true;
            for (int s = 0; s < servicios && ok; s++) ok = azar.nextDouble() < cadaUno;
            if (ok) exitosas++;
        }
        return (double) exitosas / peticiones;
    }

    public static void main(String[] args) {
        double cadena = disponibilidad(0.995, 5);
        System.out.printf("Cadena de 5 servicios: disponibilidad %.4f %% (%.1f h caído al mes), latencia %d ms%n",
                cadena * 100, horasCaidoPorMes(cadena), latenciaMs(80, 5));
        System.out.printf("Monolito:              disponibilidad %.4f %% (%.1f h caído al mes)%n",
                99.5, horasCaidoPorMes(0.995));
        System.out.printf("Simulación (1.000.000 peticiones): %.4f %%%n", simular(0.995, 5, 1_000_000, 42) * 100);
    }
}
