package acortador;

/**
 * Contador por rangos: la instancia pide un bloque de N números al coordinador (una consulta por bloque,
 * no por petición) y los reparte en memoria. El número se permuta con una clave secreta antes de codificarlo para que
 * los códigos no sean adivinables en secuencia. Al agotarse el espacio de 62^7 lanza una excepción en lugar de repetir.
 */
public class GeneradorPorRangos implements GeneradorDeCodigos {
    private final CoordinadorDeRangos coordinador;
    private final int tamano;
    private final Permutacion permutacion;
    private long siguiente;
    private long limite;
    private long bloquesPedidos;

    public GeneradorPorRangos(CoordinadorDeRangos coordinador, int tamano, Permutacion permutacion) {
        this.coordinador = coordinador;
        this.permutacion = permutacion;
        this.tamano = tamano;
    }

    @Override
    public synchronized String siguiente() {
        if (siguiente == limite) {
            long bloque = coordinador.siguienteBloque();
            if (bloque > (Base62.ESPACIO - tamano) / tamano) throw new IllegalStateException("espacio de códigos agotado (62^7)");
            siguiente = bloque * tamano;
            limite = siguiente + tamano;
            bloquesPedidos++;
        }
        return Base62.deSiete(permutacion.aplicar(siguiente++));
    }

    public synchronized long bloquesPedidos() {
        return bloquesPedidos;
    }

    @Override
    public String nombre() {
        return "rangos";
    }
}
