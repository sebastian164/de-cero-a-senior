package avisos;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.stereotype.Component;

/** Cuenta los comandos sobre `pedidos` y registra el aggregate del flujo de cambios (para ver su $match). */
@Component
public class ContadorDeComandos implements CommandListener, MongoClientSettingsBuilderCustomizer {
    private final Map<String, AtomicInteger> porNombre = new ConcurrentHashMap<>();
    private final AtomicBoolean impreso = new AtomicBoolean();

    @Override public void customize(com.mongodb.MongoClientSettings.Builder c) { c.addCommandListener(this); }

    @Override public void commandStarted(CommandStartedEvent e) {
        String n = e.getCommandName();
        var cmd = e.getCommand();
        boolean enPedidos = cmd.containsKey(n) && cmd.get(n).isString() && cmd.getString(n).getValue().equals("pedidos");
        boolean getMore = n.equals("getMore") && cmd.containsKey("collection") && cmd.getString("collection").getValue().equals("pedidos");
        if (enPedidos || getMore) porNombre.computeIfAbsent(n, k -> new AtomicInteger()).incrementAndGet();
        if (enPedidos && n.equals("aggregate") && cmd.toJson().contains("$changeStream") && impreso.compareAndSet(false, true))
            System.out.println("AGGREGATE DEL FLUJO: " + cmd.get("pipeline").toString());
    }
    public Map<String, Integer> resumen() { Map<String, Integer> m = new TreeMap<>(); porNombre.forEach((k, v) -> m.put(k, v.get())); return m; }
}
