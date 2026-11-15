public class Main {

    public static void main(String[] args) {
        Cliente cliente = new Cliente();
        cliente.nombre = "Ana";
        cliente.direccion = new Direccion();
        cliente.direccion.ciudad = new Ciudad();
        cliente.direccion.ciudad.nombre = "Tunja";
        cliente.direccion.ciudad.zona = "CENTRO";

        Pedido pedido = new Pedido();
        pedido.cliente = cliente;
        pedido.valor = 80_000;

        Configuracion.ivaPorcentaje = 19;
        System.out.println("Envío: " + Utilidades.costoEnvio(pedido));
        System.out.println("IVA: " + Utilidades.iva(pedido.valor));
        System.out.println(Utilidades.mayusculas(cliente.nombre));
    }
}

class Ciudad { String nombre; String zona; }
class Direccion { Ciudad ciudad; }
class Cliente { String nombre; Direccion direccion; }
class Pedido { Cliente cliente; long valor; }

class Configuracion {
    static int ivaPorcentaje;
}

class Utilidades {

    static long costoEnvio(Pedido pedido) {
        String zona = pedido.cliente.direccion.ciudad.zona;
        if (zona.equals("CENTRO")) {
            return 5_000;
        }
        return 12_000;
    }

    static long iva(long valor) {
        return valor * Configuracion.ivaPorcentaje / 100;
    }

    static String mayusculas(String texto) {
        return texto.toUpperCase();
    }
}
