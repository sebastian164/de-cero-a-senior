import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class Transportes {
    private Transportes() { }

    /** El único switch por tipo: traduce texto externo a objetos. */
    public static Transporte desdeTexto(String texto) {
        return switch (texto) {
            case "MOTO" -> new Moto();
            case "CAMION" -> new Camion();
            case "BICICLETA" -> new Bicicleta();
            case "DRON" -> new Dron();
            default -> throw new IllegalArgumentException("Transporte desconocido: " + texto);
        };
    }

    public static Optional<Transporte> masBarato(List<Transporte> transportes, Envio envio) {
        return transportes.stream()
                .filter(t -> t.puedeLlevar(envio))
                .min(Comparator.comparingLong(t -> t.costo(envio)));
    }
}
