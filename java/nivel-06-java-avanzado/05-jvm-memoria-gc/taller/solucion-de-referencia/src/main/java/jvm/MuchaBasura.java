package jvm;

import java.util.ArrayList;
import java.util.List;

/** Crea muchos objetos de vida corta y unos pocos de vida larga. */
public class MuchaBasura {
    public static void main(String[] args) {
        List<int[]> sobrevivientes = new ArrayList<>();
        int[][] recientes = new int[16][];              // los temporales escapan aquí: el JIT no puede eliminarlos
        long suma = 0;
        for (int i = 0; i < 3_000_000; i++) {
            int[] temporal = new int[64];               // muere joven: lo pisa otro 16 vueltas después
            temporal[0] = i;
            recientes[i & 15] = temporal;
            suma += recientes[(i + 1) & 15] == null ? 0 : recientes[(i + 1) & 15][0];
            if (i % 10_000 == 0) sobrevivientes.add(new int[1024]);   // algunos llegan a la generación vieja
        }
        System.out.println("suma=" + suma + " sobrevivientes=" + sobrevivientes.size());
    }
}
