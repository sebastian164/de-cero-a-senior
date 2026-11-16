package contenedor.notificadores;

import org.springframework.stereotype.Component;

@Component("sms")
class NotificadorSms implements Notificador {
    @Override public String canal() { return "sms"; }
}
