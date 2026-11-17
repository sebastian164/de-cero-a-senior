package tienda;

/** Los cuatro caminos por coreografía, con la traza de eventos de cada compra. */
public class Main {

    public static void main(String[] args) {
        Tienda t = new Tienda(2, false, true);
        t.envios.sinCobertura.add("c-4");
        t.comprar("c-1", 200_000);
        t.comprar("c-2", 5_000_000);
        t.comprar("c-3", 200_000);
        t.correr(1);
        t.comprar("c-4", 100_000);
        t.correr(1);
        for (String c : t.pedidos.estados.keySet()) {
            System.out.println(c + ": " + t.bus.traza.stream().filter(e -> e.correlacion().equals(c)).map(e -> e.tipo()).toList());
        }
        System.out.println(t.resumen());
        System.out.println("inconsistencias: [" + t.inconsistencias() + "]");
    }
}
