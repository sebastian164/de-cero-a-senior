import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Plantilla base = new Plantilla("Contrato de servicios");
        base.agregarSeccion("Objeto");
        base.agregarSeccion("Obligaciones");

        Plantilla paraCliente = base.copiar();
        paraCliente.agregarSeccion("Cláusula especial");

        System.out.println("Base:    " + base);
        System.out.println("Cliente: " + paraCliente);
    }
}

interface Copiable<T> {
    T copiar();
}

class Plantilla implements Copiable<Plantilla> {

    private final String titulo;
    private final List<String> secciones;

    Plantilla(String titulo) {
        this.titulo = titulo;
        this.secciones = new ArrayList<>();
    }

    /** Constructor de copia: copia profunda de lo mutable. */
    private Plantilla(Plantilla otra) {
        this.titulo = otra.titulo;
        this.secciones = new ArrayList<>(otra.secciones);
    }

    @Override
    public Plantilla copiar() {
        return new Plantilla(this);
    }

    void agregarSeccion(String seccion) {
        secciones.add(seccion);
    }

    @Override
    public String toString() {
        return titulo + " " + secciones;
    }
}
