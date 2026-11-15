import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class Main {

    public static void main(String[] args) {
        ClientesPaginados clientes = new ClientesPaginados(new FuenteSimulada(), 2);
        for (String cliente : clientes) {          // el cliente no sabe que hay páginas
            System.out.println(cliente);
        }
    }
}

class FuenteSimulada {
    private final List<String> datos = List.of("Ana", "Luis", "Marta", "Pedro", "Sofía");

    List<String> pagina(int numero, int tamano) {
        System.out.println("  (consulta página " + numero + ")");
        int desde = Math.min(numero * tamano, datos.size());
        int hasta = Math.min(desde + tamano, datos.size());
        return datos.subList(desde, hasta);
    }
}

/** Implementar Iterable permite usar for-each y oculta la paginación. */
class ClientesPaginados implements Iterable<String> {

    private final FuenteSimulada fuente;
    private final int tamanoPagina;

    ClientesPaginados(FuenteSimulada fuente, int tamanoPagina) {
        this.fuente = fuente;
        this.tamanoPagina = tamanoPagina;
    }

    @Override
    public Iterator<String> iterator() {
        return new Iterator<>() {
            private int pagina = 0;
            private Iterator<String> actual = fuente.pagina(0, tamanoPagina).iterator();

            @Override
            public boolean hasNext() {
                if (actual.hasNext()) {
                    return true;
                }
                actual = fuente.pagina(++pagina, tamanoPagina).iterator();
                return actual.hasNext();
            }

            @Override
            public String next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return actual.next();
            }
        };
    }
}
