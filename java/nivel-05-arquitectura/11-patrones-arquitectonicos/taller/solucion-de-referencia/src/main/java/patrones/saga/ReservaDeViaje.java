package patrones.saga;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Vuelo, hotel y carro como tres "servicios" simulados con su propio estado. */
public class ReservaDeViaje {

    public static class Viaje {
        public final String id;
        public Viaje(String id) { this.id = id; }
    }

    /** Servicio simulado: reserva y libera de forma idempotente; puede configurarse para fallar. */
    public static class Servicio implements Paso<Viaje> {
        private final String nombre;
        public final Set<String> reservas = new HashSet<>();
        public boolean fallarAlReservar;
        public int fallosAlCompensar;

        public Servicio(String nombre) { this.nombre = nombre; }

        @Override public String nombre() { return nombre; }

        @Override public void ejecutar(Viaje v) {
            if (fallarAlReservar) throw new IllegalStateException("sin disponibilidad");
            reservas.add(v.id);                         // idempotente: un reintento no duplica
        }

        @Override public void compensar(Viaje v) {
            if (fallosAlCompensar > 0) { fallosAlCompensar--; throw new IllegalStateException("tiempo agotado"); }
            reservas.remove(v.id);                      // idempotente: liberar dos veces no falla
        }
    }

    public final Servicio vuelo = new Servicio("vuelo");
    public final Servicio hotel = new Servicio("hotel");
    public final Servicio carro = new Servicio("carro");

    public Orquestador.Resultado reservar(String viajeId) {
        return new Orquestador<>(List.<Paso<Viaje>>of(vuelo, hotel, carro)).ejecutar(new Viaje(viajeId));
    }
}
