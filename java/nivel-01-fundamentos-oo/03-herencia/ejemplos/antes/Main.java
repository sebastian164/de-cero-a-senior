import java.util.ArrayList;

public class Main {

    public static void main(String[] args) {
        Pila pila = new Pila();
        pila.apilar("plato 1");
        pila.apilar("plato 2");
        pila.apilar("plato 3");
        System.out.println("Tope: " + pila.desapilar());

        // Todo esto es legal, y destruye la idea de pila.
        pila.add(0, "plato colado por debajo");
        pila.remove("plato 2");
        pila.clear();
        System.out.println("¿Vacía? " + pila.isEmpty());
    }
}

/** Hereda de ArrayList para "aprovechar" que ya sabe guardar elementos. */
class Pila extends ArrayList<String> {

    void apilar(String elemento) {
        add(elemento);
    }

    String desapilar() {
        return remove(size() - 1);
    }
}
