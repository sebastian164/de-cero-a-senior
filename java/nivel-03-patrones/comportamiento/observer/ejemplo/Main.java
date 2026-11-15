import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        ServicioPedidos pedidos = new ServicioPedidos();
        pedidos.suscribir(evento -> System.out.println("Correo: pedido " + evento.numero() + " confirmado"));
        pedidos.suscribir(evento -> System.out.println("Inventario: descontar pedido " + evento.numero()));
        pedidos.suscribir(new AcumuladorDePuntos());

        pedidos.confirmar("P-100", 180_000);
    }
}

/** El hecho, en pasado, con los datos que los interesados necesitan. */
record PedidoConfirmado(String numero, long valor) { }

interface OyentePedidos {
    void alConfirmar(PedidoConfirmado evento);
}

/** El sujeto: publica lo que ocurrió y no sabe quién escucha. */
class ServicioPedidos {

    private final List<OyentePedidos> oyentes = new ArrayList<>();

    void suscribir(OyentePedidos oyente) {
        oyentes.add(oyente);
    }

    void confirmar(String numero, long valor) {
        System.out.println("Pedido " + numero + " confirmado");
        PedidoConfirmado evento = new PedidoConfirmado(numero, valor);
        for (OyentePedidos oyente : oyentes) {
            oyente.alConfirmar(evento);
        }
    }
}

class AcumuladorDePuntos implements OyentePedidos {
    @Override
    public void alConfirmar(PedidoConfirmado evento) {
        System.out.println("Puntos: +" + evento.valor() / 1_000);
    }
}
