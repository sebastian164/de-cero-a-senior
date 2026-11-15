import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Map<String, CalculoEnvio> modalidades = Map.of(
                "ESTANDAR", new EnvioEstandar(),
                "EXPRESS", new EnvioExpress(),
                "RECOGIDA", pesoKg -> 0);   // una estrategia también puede ser una lambda

        Carrito carrito = new Carrito(4, modalidades.get("EXPRESS"));
        System.out.println("Express: " + carrito.costoEnvio());

        carrito.cambiarEnvio(modalidades.get("RECOGIDA"));
        System.out.println("Recogida: " + carrito.costoEnvio());
    }
}

/** La estrategia: una familia de algoritmos intercambiables. */
@FunctionalInterface
interface CalculoEnvio {
    long calcular(int pesoKg);
}

class EnvioEstandar implements CalculoEnvio {
    @Override public long calcular(int pesoKg) { return 8_000 + pesoKg * 500L; }
}

class EnvioExpress implements CalculoEnvio {
    @Override public long calcular(int pesoKg) { return 15_000 + pesoKg * 900L; }
}

/** El contexto: usa una estrategia sin saber cuál es. */
class Carrito {

    private final int pesoKg;
    private CalculoEnvio calculoEnvio;

    Carrito(int pesoKg, CalculoEnvio calculoEnvio) {
        this.pesoKg = pesoKg;
        this.calculoEnvio = calculoEnvio;
    }

    void cambiarEnvio(CalculoEnvio nuevo) {
        this.calculoEnvio = nuevo;
    }

    long costoEnvio() {
        return calculoEnvio.calcular(pesoKg);
    }
}
