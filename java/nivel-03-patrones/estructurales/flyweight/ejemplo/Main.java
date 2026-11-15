import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        CatalogoTipos catalogo = new CatalogoTipos();
        List<Marcador> marcadores = new ArrayList<>();

        for (int i = 0; i < 100_000; i++) {
            String tipo = i % 2 == 0 ? "Hospital" : "Colegio";
            marcadores.add(new Marcador(4.0 + i * 0.00001, -74.0, catalogo.obtener(tipo)));
        }

        System.out.println("Marcadores: " + marcadores.size());
        System.out.println("Objetos TipoMarcador creados: " + catalogo.creados());
        System.out.println(marcadores.get(0).describir());
    }
}

/** Estado intrínseco: compartido e inmutable. */
record TipoMarcador(String nombre, String icono, String color) { }

/** La fábrica garantiza que cada tipo se crea una sola vez. */
class CatalogoTipos {

    private final Map<String, TipoMarcador> tipos = new HashMap<>();

    TipoMarcador obtener(String nombre) {
        return tipos.computeIfAbsent(nombre,
                n -> new TipoMarcador(n, n.toLowerCase() + ".png", n.equals("Hospital") ? "#FF0000" : "#0000FF"));
    }

    int creados() {
        return tipos.size();
    }
}

/** Estado extrínseco (la posición) más una referencia al compartido. */
class Marcador {

    private final double latitud;
    private final double longitud;
    private final TipoMarcador tipo;

    Marcador(double latitud, double longitud, TipoMarcador tipo) {
        this.latitud = latitud;
        this.longitud = longitud;
        this.tipo = tipo;
    }

    String describir() {
        return tipo.nombre() + " en (" + latitud + ", " + longitud + ") icono " + tipo.icono();
    }
}
