public class Main {

    public static void main(String[] args) {
        Cliente cliente = new Cliente("Ana", new Direccion(new Ciudad("Tunja", Zona.CENTRO)));
        Pedido pedido = new Pedido(cliente, 80_000);

        TarifaEnvio tarifa = new TarifaEnvio();
        CalculadoraIva iva = new CalculadoraIva(19);

        System.out.println("Envío: " + tarifa.costo(pedido.zonaDeEntrega()));
        System.out.println("IVA: " + iva.calcular(pedido.valor()));
        System.out.println(cliente.nombre().toUpperCase());
    }
}

enum Zona { CENTRO, PERIFERIA }

class Ciudad {
    private final String nombre;
    private final Zona zona;
    Ciudad(String nombre, Zona zona) { this.nombre = nombre; this.zona = zona; }
    String nombre() { return nombre; }
    Zona zona() { return zona; }
}

class Direccion {
    private final Ciudad ciudad;
    Direccion(Ciudad ciudad) { this.ciudad = ciudad; }
    Zona zona() { return ciudad.zona(); }
}

class Cliente {
    private final String nombre;
    private final Direccion direccion;
    Cliente(String nombre, Direccion direccion) { this.nombre = nombre; this.direccion = direccion; }
    String nombre() { return nombre; }
    Zona zonaDeEntrega() { return direccion.zona(); }
}

class Pedido {
    private final Cliente cliente;
    private final long valor;
    Pedido(Cliente cliente, long valor) { this.cliente = cliente; this.valor = valor; }
    long valor() { return valor; }
    Zona zonaDeEntrega() { return cliente.zonaDeEntrega(); }
}

/** Solo sabe de tarifas. No sabe qué es un pedido ni un cliente. */
class TarifaEnvio {
    long costo(Zona zona) {
        return zona == Zona.CENTRO ? 5_000 : 12_000;
    }
}

/** Lo que necesita para trabajar lo recibe al crearse. No hay orden oculto. */
class CalculadoraIva {
    private final int porcentaje;

    CalculadoraIva(int porcentaje) {
        if (porcentaje < 0 || porcentaje > 100) {
            throw new IllegalArgumentException("Porcentaje de IVA inválido");
        }
        this.porcentaje = porcentaje;
    }

    long calcular(long valor) {
        return valor * porcentaje / 100;
    }
}
