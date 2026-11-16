package tienda.pedidos.memoria;

import org.springframework.stereotype.Component;
import tienda.pedidos.aplicacion.Notificador;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class NotificadorEnMemoria implements Notificador {
    public final List<String> enviados = new CopyOnWriteArrayList<>();

    @Override public void enviar(String destinatario, String mensaje) { enviados.add(destinatario + ": " + mensaje); }
}
