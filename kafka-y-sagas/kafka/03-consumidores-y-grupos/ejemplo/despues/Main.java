import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Un consumidor que confirma la posición DESPUÉS de procesar el mensaje. */
public class Main {

    static final List<String> PARTICION = List.of("pago-1", "pago-2", "pago-3", "pago-4", "pago-5");
    static int posicionConfirmada = 0;
    static final List<String> procesados = new ArrayList<>();

    public static void main(String[] args) {
        try {
            consumir(true);                                  // se cae justo después de procesar pago-3
        } catch (IllegalStateException caida) {
            System.out.println("  >> el proceso se cayó: " + caida.getMessage());
        }
        System.out.println("  >> reinicio: retomo desde la posición confirmada " + posicionConfirmada);
        consumir(false);

        List<String> perdidos = new ArrayList<>(PARTICION);
        perdidos.removeAll(procesados);
        Set<String> vistos = new LinkedHashSet<>();
        Set<String> repetidos = new LinkedHashSet<>();
        for (String mensaje : procesados) {
            if (!vistos.add(mensaje)) {
                repetidos.add(mensaje);
            }
        }
        System.out.println();
        System.out.println("En la partición: " + PARTICION);
        System.out.println("Procesados:      " + procesados);
        System.out.println("Perdidos:        " + perdidos);
        System.out.println("Repetidos:       " + repetidos);
    }

    static void consumir(boolean caerseTrasElTercero) {
        int posicion = posicionConfirmada;
        while (posicion < PARTICION.size()) {
            String mensaje = PARTICION.get(posicion);
            procesados.add(mensaje);                         // 1. procesa
            System.out.println("procesado " + mensaje);
            if (caerseTrasElTercero && mensaje.equals("pago-3")) {
                throw new IllegalStateException("caída después de procesar " + mensaje + " y antes de confirmar");
            }
            posicion++;
            posicionConfirmada = posicion;                   // 2. confirma
            System.out.println("confirmada la posición " + posicionConfirmada);
        }
    }
}
