package contenedor.notificadores;

import org.springframework.stereotype.Component;

@org.springframework.context.annotation.Primary
@Component("correo")
class NotificadorCorreo implements Notificador {
    @Override public String canal() { return "correo"; }
}
