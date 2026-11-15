import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Una cadena de llamadas directas: cada servicio conoce al siguiente y deshace lo suyo si el otro falla. */
public class Main {

    static final class Pagos {
        final Set<String> cobros = new LinkedHashSet<>();
        final List<String> correos = new ArrayList<>();

        void cobrar(String viaje, int valor) {
            if (valor > 1000) {
                throw new IllegalStateException("pago rechazado");
            }
            cobros.add(viaje);
            correos.add("confirmación de " + viaje);       // "ya que estamos aquí": Pagos enviando correos
        }
    }

    static final class Hoteles {
        final Set<String> reservas = new LinkedHashSet<>();
        final Pagos pagos;

        Hoteles(Pagos pagos) {
            this.pagos = pagos;
        }

        void reservar(String viaje, int valor) {
            reservas.add(viaje);
            try {
                pagos.cobrar(viaje, valor);                // Hoteles llama a Pagos
            } catch (IllegalStateException error) {
                reservas.remove(viaje);
                throw error;
            }
        }
    }

    static final class Vuelos {
        final Set<String> reservas = new LinkedHashSet<>();
        final Hoteles hoteles;

        Vuelos(Hoteles hoteles) {
            this.hoteles = hoteles;
        }

        void reservar(String viaje, int valor) {
            reservas.add(viaje);
            try {
                hoteles.reservar(viaje, valor);            // Vuelos llama a Hoteles
            } catch (IllegalStateException error) {
                reservas.remove(viaje);
                throw error;
            }
        }
    }

    public static void main(String[] args) {
        Pagos pagos = new Pagos();
        Hoteles hoteles = new Hoteles(pagos);
        Vuelos vuelos = new Vuelos(hoteles);

        vuelos.reservar("viaje-1", 800);
        try {
            vuelos.reservar("viaje-2", 5000);
        } catch (IllegalStateException error) {
            System.out.println("viaje-2: " + error.getMessage());
        }

        System.out.println("Vuelos:  " + vuelos.reservas);
        System.out.println("Hoteles: " + hoteles.reservas);
        System.out.println("Cobros:  " + pagos.cobros);
        System.out.println("Correos: " + pagos.correos);
    }
}
