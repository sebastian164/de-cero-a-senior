import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Pila pila = new Pila();
        pila.apilar("plato 1");
        pila.apilar("plato 2");
        System.out.println("Tope: " + pila.desapilar());
        // pila.add(0, "colado");  // no compila: Pila no expone eso

        List<Empleado> nomina = List.of(
                new EmpleadoAsalariado("Ana", 4_000_000),
                new EmpleadoPorHoras("Luis", 25_000, 120));
        for (Empleado empleado : nomina) {
            System.out.println(empleado.resumen());
        }
    }
}

/** La pila TIENE una lista. No ES una lista. */
class Pila {

    private final List<String> elementos = new ArrayList<>();

    void apilar(String elemento) {
        elementos.add(elemento);
    }

    String desapilar() {
        if (elementos.isEmpty()) {
            throw new IllegalStateException("La pila está vacía");
        }
        return elementos.remove(elementos.size() - 1);
    }
}

abstract class Empleado {

    private final String nombre;

    protected Empleado(String nombre) {
        this.nombre = nombre;
    }

    abstract long pagoMensual();

    final String resumen() {
        return nombre + " cobra $" + pagoMensual();
    }
}

class EmpleadoAsalariado extends Empleado {

    private final long salario;

    EmpleadoAsalariado(String nombre, long salario) {
        super(nombre);
        this.salario = salario;
    }

    @Override
    long pagoMensual() {
        return salario;
    }
}

class EmpleadoPorHoras extends Empleado {

    private final long valorHora;
    private final int horas;

    EmpleadoPorHoras(String nombre, long valorHora, int horas) {
        super(nombre);
        this.valorHora = valorHora;
        this.horas = horas;
    }

    @Override
    long pagoMensual() {
        return valorHora * horas;
    }
}
