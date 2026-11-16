package colecciones;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;

/** 100.000 búsquedas sobre un millón de elementos: ArrayList.contains frente a HashSet.contains. */
public class MedirBusquedas {

    public record Medicion(String coleccion, long milisegundos, int encontrados) { }

    static final int ELEMENTOS = 1_000_000;
    static final int BUSQUEDAS = 100_000;

    public static Medicion medir(String nombre, Collection<Integer> coleccion, int[] claves) {
        long inicio = System.nanoTime();
        int encontrados = 0;
        for (int clave : claves) if (coleccion.contains(clave)) encontrados++;
        return new Medicion(nombre, (System.nanoTime() - inicio) / 1_000_000, encontrados);
    }

    public static List<Medicion> ejecutar() {
        List<Integer> lista = new ArrayList<>(ELEMENTOS);
        for (int i = 0; i < ELEMENTOS; i++) lista.add(i);
        HashSet<Integer> conjunto = new HashSet<>(lista);
        // claves entre 0 y 2.000.000: la mitad existe, la otra mitad no (el peor caso de la lista)
        int[] claves = new SplittableRandom(7).ints(BUSQUEDAS, 0, 2 * ELEMENTOS).toArray();
        medir("calentamiento", conjunto, claves);
        return List.of(medir("ArrayList", lista, claves), medir("HashSet", conjunto, claves));
    }

    public static void main(String[] args) {
        for (Medicion m : ejecutar()) {
            System.out.printf("%-10s %,8d ms  (%,d encontrados)%n", m.coleccion(), m.milisegundos(), m.encontrados());
        }
    }
}
