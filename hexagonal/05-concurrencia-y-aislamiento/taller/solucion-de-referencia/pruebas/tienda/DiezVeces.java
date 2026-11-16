package tienda;

import java.util.ArrayList;
import java.util.List;

/** Paso 7: la prueba con hilos, diez veces seguidas. Termina con código 1 si alguna falla. */
public final class DiezVeces {

    private DiezVeces() {
    }

    public static void main(String[] args) {
        List<Integer> rendidos = new ArrayList<>();
        for (int vez = 1; vez <= 10; vez++) {
            System.out.println("Ejecución " + vez + ":");
            rendidos.add(PruebasDeConcurrencia.muchosHilos());
        }
        System.out.println("rendidos por ejecución: " + rendidos);
        System.out.println(Comprobar.total() + " comprobaciones, " + Comprobar.fallos() + " fallos");
        System.exit(Comprobar.fallos() == 0 ? 0 : 1);
    }
}
