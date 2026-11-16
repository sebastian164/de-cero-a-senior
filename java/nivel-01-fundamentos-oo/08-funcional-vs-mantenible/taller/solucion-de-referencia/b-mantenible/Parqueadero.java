import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Quién está adentro, cuántos cupos quedan y el cobro al salir. */
public final class Parqueadero {

    private record Estadia(Vehiculo vehiculo, LocalDateTime entrada) { }

    private final Map<String, Estadia> adentro = new HashMap<>();
    private final Map<TipoVehiculo, Integer> ocupados = new EnumMap<>(TipoVehiculo.class);
    private final CalculadoraCobro calculadora;

    public Parqueadero(CalculadoraCobro calculadora) {
        this.calculadora = calculadora;
    }

    public void registrarEntrada(Vehiculo vehiculo, LocalDateTime hora) {
        if (adentro.containsKey(vehiculo.placa())) {
            throw new IllegalStateException("La placa " + vehiculo.placa() + " ya está adentro");
        }
        if (cuposLibres(vehiculo.tipo()) == 0) {
            throw new IllegalStateException("No hay cupo para " + vehiculo.tipo());
        }
        adentro.put(vehiculo.placa(), new Estadia(vehiculo, hora));
        ocupados.merge(vehiculo.tipo(), 1, Integer::sum);
    }

    public long registrarSalida(String placa, LocalDateTime hora) {
        Estadia estadia = adentro.get(placa.trim().toUpperCase());
        if (estadia == null) {
            throw new IllegalStateException("La placa " + placa + " no ha entrado");
        }
        long valor = calculadora.cobrar(estadia.vehiculo(), Duration.between(estadia.entrada(), hora));
        adentro.remove(estadia.vehiculo().placa());
        ocupados.merge(estadia.vehiculo().tipo(), -1, Integer::sum);
        return valor;
    }

    public int cuposLibres(TipoVehiculo tipo) {
        return tipo.cupo() - ocupados.getOrDefault(tipo, 0);
    }
}
