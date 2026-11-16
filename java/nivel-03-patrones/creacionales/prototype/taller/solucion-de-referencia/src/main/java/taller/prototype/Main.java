package taller.prototype;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        // Paso 1: el error de la copia superficial.
        Pedido original = Pedido.nuevo(1001, "Ana", new Direccion("Calle 10 # 5-20", "Tunja"), LocalDate.of(2025, 5, 1));
        original.agregarItem(new Item("Café", 2));
        Pedido superficial = original.copiaSuperficial();
        superficial.direccion().setCiudad("Duitama");     // el cliente cambia la dirección del pedido nuevo
        superficial.items().get(0).setCantidad(10);       // y la cantidad
        System.out.println("Superficial -> original quedó: " + original);

        // Paso 2: la copia correcta.
        Pedido otro = Pedido.nuevo(1002, "Luis", new Direccion("Cra 7 # 1-1", "Paipa"), LocalDate.of(2025, 5, 1));
        otro.agregarItem(new Item("Té", 1));
        otro.pagar();
        Pedido repetido = otro.repetir(2002, LocalDate.of(2025, 6, 1));
        repetido.direccion().setCiudad("Sogamoso");
        repetido.items().get(0).setCantidad(5);
        System.out.println("Profunda -> original: " + otro);
        System.out.println("Profunda -> repetido: " + repetido);

        // Paso 3: con valores inmutables, compartir es seguro.
        PedidoConValores v = new PedidoConValores(3001, "Ana", new DireccionInmutable("Calle 10", "Tunja"),
                List.of(new ItemInmutable("Café", 2)), LocalDate.of(2025, 5, 1), Estado.ENTREGADO);
        System.out.println("Inmutable -> " + v.repetir(3002, LocalDate.of(2025, 6, 1)));

        // Paso 4: clone() comparte la lista.
        System.out.println("clone() comparte la lista: " + ConClone.demostrar());
    }
}

enum Estado { CREADO, PAGADO, ENVIADO, ENTREGADO }

/** Mutable a propósito (pasos 1 y 2). */
final class Direccion {
    private String calle;
    private String ciudad;
    Direccion(String calle, String ciudad) { this.calle = calle; this.ciudad = ciudad; }
    Direccion(Direccion otra) { this(otra.calle, otra.ciudad); }
    String calle() { return calle; }
    String ciudad() { return ciudad; }
    void setCiudad(String ciudad) { this.ciudad = ciudad; }
    @Override public String toString() { return calle + ", " + ciudad; }
}

final class Item {
    private final String producto;
    private int cantidad;
    Item(String producto, int cantidad) { this.producto = producto; this.cantidad = cantidad; }
    Item(Item otro) { this(otro.producto, otro.cantidad); }
    String producto() { return producto; }
    int cantidad() { return cantidad; }
    void setCantidad(int cantidad) { this.cantidad = cantidad; }
    @Override public String toString() { return producto + " x" + cantidad; }
}

final class Pedido {
    private final long numero;
    private final String cliente;
    private final Direccion direccion;
    private final List<Item> items;
    private final LocalDate fecha;
    private Estado estado;

    private Pedido(long numero, String cliente, Direccion direccion, List<Item> items, LocalDate fecha, Estado estado) {
        this.numero = numero; this.cliente = cliente; this.direccion = direccion;
        this.items = items; this.fecha = fecha; this.estado = estado;
    }

    static Pedido nuevo(long numero, String cliente, Direccion direccion, LocalDate fecha) {
        return new Pedido(numero, cliente, direccion, new ArrayList<>(), fecha, Estado.CREADO);
    }

    /** Paso 1: INCORRECTA. Copia las referencias: comparte dirección, lista e ítems. */
    Pedido copiaSuperficial() {
        return new Pedido(numero, cliente, direccion, items, fecha, estado);
    }

    /**
     * Paso 2: correcta. Copia profunda de lo mutable (dirección, lista y cada ítem).
     * NO se copian: el número (es la identidad del pedido nuevo), la fecha (es la de hoy)
     * ni el estado (un pedido repetido empieza en CREADO, sin pago).
     */
    Pedido repetir(long numeroNuevo, LocalDate hoy) {
        List<Item> copia = new ArrayList<>();
        for (Item i : items) copia.add(new Item(i));
        return new Pedido(numeroNuevo, cliente, new Direccion(direccion), copia, hoy, Estado.CREADO);
    }

    void agregarItem(Item item) { items.add(item); }
    void pagar() { estado = Estado.PAGADO; }
    long numero() { return numero; }
    Direccion direccion() { return direccion; }
    List<Item> items() { return items; }
    LocalDate fecha() { return fecha; }
    Estado estado() { return estado; }

    @Override public String toString() {
        return "#" + numero + " " + cliente + " " + fecha + " " + estado + " a [" + direccion + "] " + items;
    }
}

// ----- Paso 3: valores inmutables -----

record DireccionInmutable(String calle, String ciudad) {
    DireccionInmutable conCiudad(String nueva) { return new DireccionInmutable(calle, nueva); }
}

record ItemInmutable(String producto, int cantidad) {
    ItemInmutable conCantidad(int nueva) { return new ItemInmutable(producto, nueva); }
}

record PedidoConValores(long numero, String cliente, DireccionInmutable direccion, List<ItemInmutable> items,
                        LocalDate fecha, Estado estado) {
    PedidoConValores {
        items = List.copyOf(items);
    }

    /** Toda la lógica de copia profunda desapareció: compartir valores inmutables es seguro. */
    PedidoConValores repetir(long numeroNuevo, LocalDate hoy) {
        return new PedidoConValores(numeroNuevo, cliente, direccion, items, hoy, Estado.CREADO);
    }
}

// ----- Paso 4: por qué no clone() -----

class ConClone implements Cloneable {
    final List<String> etiquetas = new ArrayList<>();   // final: clone() no puede darle una lista nueva

    @Override
    public ConClone clone() {
        try {
            return (ConClone) super.clone();   // copia superficial; no pasa por ningún constructor
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    static boolean demostrar() {
        ConClone a = new ConClone();
        a.etiquetas.add("urgente");
        ConClone b = a.clone();
        b.etiquetas.add("frágil");
        return a.etiquetas.contains("frágil") && a.etiquetas == b.etiquetas;
    }
}
