package auditoria;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Cliente de un servicio de auditoría (simulado: acumula lo que enviaría a la URL). */
public class ClienteAuditoria {
    private final String url;
    private final List<String> enviados = new CopyOnWriteArrayList<>();

    public ClienteAuditoria(String url) { this.url = url; }

    public void registrar(String quien, String accion) { enviados.add(quien + " " + accion + " -> " + url); }
    public String url() { return url; }
    public List<String> enviados() { return List.copyOf(enviados); }
}
