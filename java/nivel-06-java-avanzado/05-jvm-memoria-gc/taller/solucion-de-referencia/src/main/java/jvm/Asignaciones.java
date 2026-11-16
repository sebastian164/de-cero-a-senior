package jvm;

import java.util.ArrayList;
import java.util.List;

/** Tres métodos que asignan memoria en proporciones muy distintas, para encontrar el mayor con JFR. */
public class Asignaciones {

    static String armarReporte(int i) {                   // el que más asigna: cadenas y StringBuilder grandes
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < 200; j++) sb.append("linea ").append(i).append('-').append(j).append('\n');
        return sb.toString();
    }

    static int[] calcularTotales(int i) { return new int[16]; }                 // poco

    static List<Integer> idsDelLote(int i) {                                    // algo
        List<Integer> ids = new ArrayList<>();
        for (int j = 0; j < 10; j++) ids.add(i * 1000 + j);
        return ids;
    }

    public static void main(String[] args) {
        long acumulado = 0;
        long fin = System.currentTimeMillis() + 4_000;
        int i = 0;
        while (System.currentTimeMillis() < fin) {
            acumulado += armarReporte(i).length() + calcularTotales(i).length + idsDelLote(i).size();
            i++;
        }
        System.out.println("iteraciones=" + i + " acumulado=" + acumulado);
    }
}
