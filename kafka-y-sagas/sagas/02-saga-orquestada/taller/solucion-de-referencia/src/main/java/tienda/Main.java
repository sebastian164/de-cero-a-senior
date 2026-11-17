package tienda;

/** Paso 3: los cuatro caminos, con sus transiciones y el estado final de cada servicio. */
public class Main {

    public static void main(String[] args) {
        Tienda tienda = new Tienda(2, true, false);
        tienda.comprar("s-1", 200_000);        // éxito
        tienda.comprar("s-2", 5_000_000);      // pago rechazado: reserva la segunda unidad y la libera
        tienda.comprar("s-3", 300_000);        // sin existencias: cuando pide, las dos unidades están reservadas
        tienda.correr(10);
        tienda.comprar("s-5", 100_000);        // éxito: toma la unidad que liberó s-2
        tienda.correr(10);
        imprimir(tienda);

        Tienda conEnvio = new Tienda(5, true, false);
        conEnvio.envios.sinCobertura.add("s-4");
        conEnvio.comprar("s-4", 100_000);      // fallo de envío después de cobrar y reservar
        conEnvio.correr(10);
        imprimir(conEnvio);
    }

    static void imprimir(Tienda tienda) {
        tienda.almacen.sagas.values().forEach(s -> {
            System.out.println(s.id + " → " + s.estado);
            s.transiciones.forEach(t -> System.out.println("    " + t));
        });
        System.out.println("estado de los servicios: " + tienda.resumen());
        System.out.println("inconsistencias: [" + tienda.inconsistencias() + "]");
        System.out.println();
    }
}
