package tienda.pedidos.mensajeria;

import org.springframework.stereotype.Component;
import tienda.pedidos.aplicacion.CancelarPedido;
import tienda.pedidos.dominio.CancelacionRechazada;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Segundo canal: un consumidor de mensajes simulado. Comparte el caso de uso y el dominio. */
@Component
public class ConsumidorCancelaciones {

    public final List<String> rechazos = new CopyOnWriteArrayList<>();
    private final CancelarPedido cancelarPedido;

    ConsumidorCancelaciones(CancelarPedido cancelarPedido) { this.cancelarPedido = cancelarPedido; }

    /** El mensaje trae el id del pedido como texto. Un rechazo no se reintenta: se registra. */
    public void alRecibir(String mensaje) {
        try {
            cancelarPedido.ejecutar(Long.parseLong(mensaje.trim()));
        } catch (CancelacionRechazada e) {
            rechazos.add(mensaje + ": " + e.getMessage());
        }
    }
}
