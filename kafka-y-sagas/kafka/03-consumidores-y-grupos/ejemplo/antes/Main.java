import java.util.ArrayList;
import java.util.List;

/** Un consumidor que confirma la posición ANTES de procesar el mensaje. */
public class Main {

    static final List<String> PARTICION = List.of("pago-1", "pago-2", "pago-3", "pago-4", "pago-5");
    static int posicionConfirmada = 0;                       // lo que el grupo tiene guardado en Kafka
    static final List<String> procesados = new ArrayList<>();

    public static void main(String[] args) {
        try {
            consumir(true);                                  // primera ejecución: se cae en pago-3
        } catch (IllegalStateException caida) {
            System.out.println("  >> el proceso se cayó: " + caida.getMessage());
        }
        System.out.println("  >> reinicio: retomo desde la posición confirmada " + posicionConfirmada);
        consumir(false);

        List<String> perdidos = new ArrayList<>(PARTICION);
        perdidos.removeAll(procesados);
        System.out.println();
        System.out.println("En la partición: " + PARTICION);
        System.out.println("Procesados:      " + procesados);
        System.out.println("Perdidos:        " + perdidos);
    }

    static void consumir(boolean fallarEnElTercero) {
        while (posicionConfirmada < PARTICION.size()) {
            String mensaje = PARTICION.get(posicionConfirmada);
            posicionConfirmada++;                            // 1. confirma: "ya lo tengo"
            System.out.println("confirmada la posición " + posicionConfirmada + "; procesando " + mensaje);
            if (fallarEnElTercero && mensaje.equals("pago-3")) {
                throw new IllegalStateException("error al procesar " + mensaje);
            }
            procesados.add(mensaje);                         // 2. procesa
        }
    }
}
