import java.util.List;

public class Main {

    public static void main(String[] args) {
        // Variante clásica: atiende el primero que puede.
        Aprobador cadena = new Aprobador("Supervisor", 500_000,
                new Aprobador("Gerente", 5_000_000,
                        new Aprobador("Director", 50_000_000, null)));
        System.out.println(cadena.aprobar(300_000));
        System.out.println(cadena.aprobar(8_000_000));
        System.out.println(cadena.aprobar(90_000_000));

        // Variante tubería: todos procesan y cualquiera puede cortar.
        Tuberia tuberia = new Tuberia(List.of(new FiltroAutenticacion(), new FiltroRegistro()));
        System.out.println(tuberia.procesar("token-valido /pedidos"));
        System.out.println(tuberia.procesar("sin-token /pedidos"));
    }
}

class Aprobador {

    private final String cargo;
    private final long limite;
    private final Aprobador siguiente;

    Aprobador(String cargo, long limite, Aprobador siguiente) {
        this.cargo = cargo;
        this.limite = limite;
        this.siguiente = siguiente;
    }

    String aprobar(long monto) {
        if (monto <= limite) {
            return "$" + monto + " aprobado por " + cargo;
        }
        if (siguiente == null) {
            return "$" + monto + " requiere junta directiva";
        }
        return siguiente.aprobar(monto);
    }
}

interface Filtro {
    String filtrar(String solicitud, Cadena cadena);
}

interface Cadena {
    String continuar(String solicitud);
}

class Tuberia {

    private final List<Filtro> filtros;

    Tuberia(List<Filtro> filtros) {
        this.filtros = filtros;
    }

    String procesar(String solicitud) {
        return desde(0).continuar(solicitud);
    }

    private Cadena desde(int indice) {
        if (indice == filtros.size()) {
            return solicitud -> "200 OK para [" + solicitud + "]";   // el núcleo
        }
        return solicitud -> filtros.get(indice).filtrar(solicitud, desde(indice + 1));
    }
}

class FiltroAutenticacion implements Filtro {
    @Override
    public String filtrar(String solicitud, Cadena cadena) {
        if (!solicitud.startsWith("token-valido")) {
            return "401 No autorizado";            // corta la cadena
        }
        return cadena.continuar(solicitud);
    }
}

class FiltroRegistro implements Filtro {
    @Override
    public String filtrar(String solicitud, Cadena cadena) {
        System.out.println("  -> entra " + solicitud);
        String respuesta = cadena.continuar(solicitud);
        System.out.println("  <- sale " + respuesta);
        return respuesta;
    }
}
