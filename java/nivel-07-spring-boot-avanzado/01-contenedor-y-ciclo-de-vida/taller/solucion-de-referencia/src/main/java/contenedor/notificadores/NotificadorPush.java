package contenedor.notificadores;

import org.springframework.stereotype.Component;

@Component("push")
class NotificadorPush implements Notificador {
    @Override public String canal() { return "push"; }
}
