import java.util.LinkedHashSet;
import java.util.Set;

/** Una operación de tres servicios escrita como si fuera una transacción local. */
public class Main {

    // Cada conjunto representa la base de datos de un servicio distinto
    static final Set<String> vuelos = new LinkedHashSet<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static final Set<String> cobros = new LinkedHashSet<>();

    public static void main(String[] args) {
        reservar("viaje-1", 800);
        reservar("viaje-2", 5000);                 // la tarjeta no tiene cupo

        System.out.println();
        System.out.println("Vuelos reservados:  " + vuelos);
        System.out.println("Hoteles reservados: " + hoteles);
        System.out.println("Cobros:             " + cobros);
    }

    static void reservar(String viaje, int valor) {
        System.out.println("Reservando " + viaje);
        try {
            reservarVuelo(viaje);
            reservarHotel(viaje);
            cobrar(viaje, valor);
            System.out.println("  " + viaje + " confirmado");
        } catch (IllegalStateException error) {
            System.out.println("  " + viaje + " falló: " + error.getMessage());
        }
    }

    static void reservarVuelo(String viaje) {
        vuelos.add(viaje);
        System.out.println("  vuelo reservado");
    }

    static void reservarHotel(String viaje) {
        hoteles.add(viaje);
        System.out.println("  hotel reservado");
    }

    static void cobrar(String viaje, int valor) {
        if (valor > 1000) {
            throw new IllegalStateException("pago rechazado");
        }
        cobros.add(viaje);
        System.out.println("  pago cobrado");
    }
}
