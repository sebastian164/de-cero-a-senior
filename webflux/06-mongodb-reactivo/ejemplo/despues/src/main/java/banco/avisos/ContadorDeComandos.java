package banco.avisos;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.stereotype.Component;

/** Cuenta cada comando que el driver envía a MongoDB: así se mide el costo de enterarse, no se supone. */
@Component
public class ContadorDeComandos implements CommandListener, MongoClientSettingsBuilderCustomizer {

    private final Map<String, AtomicInteger> porNombre = new ConcurrentHashMap<>();

    @Override
    public void customize(com.mongodb.MongoClientSettings.Builder configuracion) {
        configuracion.addCommandListener(this);
    }

    @Override
    public void commandStarted(CommandStartedEvent evento) {
        String nombre = evento.getCommandName();
        boolean sobreMovimientos = evento.getCommand().containsKey(nombre)
                && evento.getCommand().get(nombre).isString()
                && evento.getCommand().getString(nombre).getValue().equals("movimientos");     // find, insert, aggregate...
        boolean continuacion = nombre.equals("getMore")
                && evento.getCommand().getString("collection").getValue().equals("movimientos");   // pedir más a un cursor
        if (sobreMovimientos || continuacion) {
            porNombre.computeIfAbsent(nombre, clave -> new AtomicInteger()).incrementAndGet();
        }
    }

    public void reiniciar() {
        porNombre.clear();
    }

    public Map<String, Integer> resumen() {
        Map<String, Integer> copia = new TreeMap<>();
        porNombre.forEach((nombre, cuenta) -> copia.put(nombre, cuenta.get()));
        return copia;
    }
}
