import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        ValidadorPedido validador = new ValidadorPedido();
        System.out.println(validador.validar(new Pedido("Ana", 3, 250_000, "Tunja")));
        System.out.println(validador.validar(new Pedido("", 0, 9_000_000, "Leticia")));
    }
}

class Pedido {
    final String cliente;
    final int cantidadItems;
    final long valor;
    final String ciudad;

    Pedido(String cliente, int cantidadItems, long valor, String ciudad) {
        this.cliente = cliente;
        this.cantidadItems = cantidadItems;
        this.valor = valor;
        this.ciudad = ciudad;
    }
}

class ValidadorPedido {

    List<String> validar(Pedido pedido) {
        List<String> errores = new ArrayList<>();

        if (pedido.cliente == null || pedido.cliente.isBlank()) {
            errores.add("El pedido no tiene cliente");
        }
        if (pedido.cantidadItems <= 0) {
            errores.add("El pedido no tiene ítems");
        }
        // Agregado en marzo por riesgo
        if (pedido.valor > 5_000_000) {
            errores.add("El valor supera el máximo permitido");
        }
        // Agregado en junio por logística
        if (pedido.ciudad.equals("Leticia") || pedido.ciudad.equals("San Andrés")) {
            errores.add("No hay cobertura de envío a " + pedido.ciudad);
        }
        return errores;
    }
}
