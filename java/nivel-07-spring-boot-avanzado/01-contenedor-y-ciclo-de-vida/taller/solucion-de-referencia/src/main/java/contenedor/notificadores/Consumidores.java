package contenedor.notificadores;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Las cuatro formas de resolver varias implementaciones. */
@Component
public class Consumidores {
    public final Notificador porPrimary;
    public final Notificador porQualifier;
    public final List<Notificador> todos;
    public final Map<String, Notificador> porNombre;

    Consumidores(Notificador porPrimary,                       // @Primary decide
                 @Qualifier("sms") Notificador porQualifier,    // el nombre decide
                 List<Notificador> todos,                       // todas las implementaciones
                 Map<String, Notificador> porNombre) {          // nombre del bean -> implementación
        this.porPrimary = porPrimary;
        this.porQualifier = porQualifier;
        this.todos = todos;
        this.porNombre = porNombre;
    }
}
