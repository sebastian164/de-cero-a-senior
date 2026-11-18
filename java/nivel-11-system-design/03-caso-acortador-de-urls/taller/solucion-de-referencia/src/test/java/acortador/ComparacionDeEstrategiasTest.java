package acortador;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Punto 2 del taller: compara las estrategias por longitud, colisiones y predecibilidad (imprime la tabla). */
class ComparacionDeEstrategiasTest {
    static int distancia(String a, String b) {
        int d = 0;
        for (int i = 0; i < a.length(); i++) if (a.charAt(i) != b.charAt(i)) d++;
        return d;
    }

    static double distanciaMediaConsecutivos(GeneradorDeCodigos g, int n) {
        String previo = g.siguiente();
        long suma = 0;
        for (int i = 0; i < n; i++) {
            String actual = g.siguiente();
            suma += distancia(previo, actual);
            previo = actual;
        }
        return (double) suma / n;
    }

    /** Contador global sin permutación (la estrategia "contador en base 62" del README). */
    static class Secuencial implements GeneradorDeCodigos {
        final AtomicLong c = new AtomicLong(1_000_000);
        public String siguiente() { return Base62.deSiete(c.getAndIncrement()); }
        public String nombre() { return "contador"; }
    }

    @Test
    void comparaLasTresEstrategias() {
        int n = 5_000_000;
        // 1. Colisiones con 5 millones de códigos: el aleatorio las tiene (paradoja del cumpleaños); los de contador no.
        Set<String> vistos = new HashSet<>(n * 2);
        int colisiones = 0;
        var aleatorio = new GeneradorAleatorio();
        for (int i = 0; i < n; i++) if (!vistos.add(aleatorio.siguiente())) colisiones++;
        double esperadas = (double) n * n / (2.0 * Base62.ESPACIO);

        CoordinadorDeRangos coordinador = new CoordinadorDeRangos() {
            long bloque = 0;
            public synchronized long siguienteBloque() { return bloque++; }
        };
        var rangos = new GeneradorPorRangos(coordinador, 10_000, new PermutacionConClave("clave-secreta-de-prueba"));
        Set<String> r = new HashSet<>(n * 2);
        int colisionesRangos = 0;
        for (int i = 0; i < n; i++) if (!r.add(rangos.siguiente())) colisionesRangos++;

        // 2. Predecibilidad: caracteres distintos entre códigos consecutivos (de 7).
        double dAleatorio = distanciaMediaConsecutivos(new GeneradorAleatorio(), 100_000);
        double dSecuencial = distanciaMediaConsecutivos(new Secuencial(), 100_000);
        double dRangos = distanciaMediaConsecutivos(new GeneradorPorRangos(coordinador, 10_000, new PermutacionConClave("clave-secreta-de-prueba")), 100_000);
        double dAfin = distanciaMediaConsecutivos(new GeneradorPorRangos(coordinador, 10_000, Base62.AFIN), 100_000);

        System.out.printf("%n%-10s %-9s %-30s %-34s%n", "estrategia", "longitud", "colisiones en 5M códigos", "caracteres distintos entre consecutivos (de 7)");
        System.out.printf("%-10s %-9d %-30s %-34.2f%n", "contador", 7, "0 (único por construcción)", dSecuencial);
        System.out.printf("%-10s %-9d %-30s %-34.2f%n", "rangos+afín", 7, "0 (único por construcción)", dAfin);
        System.out.printf("%-10s %-9d %-30s %-34.2f%n", "rangos+clave", 7, colisionesRangos + " (único por construcción)", dRangos);
        System.out.printf("%-10s %-9d %-30s %-34.2f%n", "aleatorio", 7, colisiones + " (esperadas ≈ " + String.format("%.1f", esperadas) + ")", dAleatorio);

        assertEquals(0, colisionesRangos);
        assertTrue(colisiones <= esperadas * 4 + 8, "demasiadas colisiones: " + colisiones);
        assertTrue(dSecuencial < 1.5, "el contador es predecible: un solo carácter cambia casi siempre");
        assertTrue(dAfin > 4.5, "la afín esconde la adyacencia a simple vista: " + dAfin);
        assertTrue(dRangos > 5.5, "la permutación debe esconder el orden: " + dRangos);
        assertTrue(dAleatorio > 5.5);
    }
}
