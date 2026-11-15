import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        ListaConContador lista = new ListaConContador();
        lista.agregar("a");
        lista.agregarTodos(List.of("b", "c", "d"));
        System.out.println("Elementos reales: " + lista.tamano());
        System.out.println("Agregados según el contador: " + lista.totalAgregados());

        // Dos dimensiones de variación, combinadas sin crear una clase por combinación.
        Empleado ana = new Empleado("Ana", new ContratoFijo(5_000_000), new CargoGerente());
        Empleado luis = new Empleado("Luis", new ContratoPorHoras(30_000, 80), new CargoAnalista());
        System.out.println(ana.resumen());
        System.out.println(luis.resumen());
    }
}

/** TIENE una lista. Solo existen dos caminos para agregar, y ambos cuentan. */
class ListaConContador {

    private final List<String> elementos = new ArrayList<>();
    private int agregados = 0;

    void agregar(String elemento) {
        elementos.add(elemento);
        agregados++;
    }

    void agregarTodos(Collection<String> nuevos) {
        elementos.addAll(nuevos);
        agregados += nuevos.size();
    }

    int tamano() {
        return elementos.size();
    }

    int totalAgregados() {
        return agregados;
    }
}

interface Contrato {
    long pagoBase();
}

class ContratoFijo implements Contrato {
    private final long salario;
    ContratoFijo(long salario) { this.salario = salario; }
    @Override public long pagoBase() { return salario; }
}

class ContratoPorHoras implements Contrato {
    private final long valorHora;
    private final int horas;
    ContratoPorHoras(long valorHora, int horas) { this.valorHora = valorHora; this.horas = horas; }
    @Override public long pagoBase() { return valorHora * horas; }
}

interface Cargo {
    String nombre();
    long bono(long pagoBase);
}

class CargoGerente implements Cargo {
    @Override public String nombre() { return "Gerente"; }
    @Override public long bono(long pagoBase) { return pagoBase * 20 / 100; }
}

class CargoAnalista implements Cargo {
    @Override public String nombre() { return "Analista"; }
    @Override public long bono(long pagoBase) { return 0; }
}

class Empleado {

    private final String nombre;
    private final Contrato contrato;
    private Cargo cargo;

    Empleado(String nombre, Contrato contrato, Cargo cargo) {
        this.nombre = nombre;
        this.contrato = contrato;
        this.cargo = cargo;
    }

    /** Un ascenso cambia una pieza. Con herencia habría que crear otro objeto. */
    void ascender(Cargo nuevoCargo) {
        this.cargo = nuevoCargo;
    }

    long pagoMensual() {
        long base = contrato.pagoBase();
        return base + cargo.bono(base);
    }

    String resumen() {
        return nombre + " (" + cargo.nombre() + ") cobra $" + pagoMensual();
    }
}
