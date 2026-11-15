import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        ListaConContador lista = new ListaConContador();
        lista.add("a");
        lista.addAll(List.of("b", "c", "d"));
        System.out.println("Elementos reales: " + lista.size());
        System.out.println("Agregados según el contador: " + lista.totalAgregados());
    }
}

/** Una lista que cuenta cuántos elementos se le han agregado en toda su vida. */
@SuppressWarnings("serial")
class ListaConContador extends ArrayList<String> {

    private int agregados = 0;

    @Override
    public boolean add(String elemento) {
        agregados++;
        return super.add(elemento);
    }

    @Override
    public boolean addAll(Collection<? extends String> elementos) {
        agregados += elementos.size();
        return super.addAll(elementos);
    }

    int totalAgregados() {
        return agregados;
    }
}
