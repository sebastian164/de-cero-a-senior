public class Main {

    public static void main(String[] args) {
        ServicioPedidos servicio = new ServicioPedidos();
        servicio.confirmar("P-100", "ana@correo.example", 180_000);
    }
}

class ServicioPedidos {

    private final RepositorioMySql repositorio = new RepositorioMySql("jdbc:mysql://prod-db:3306/tienda");
    private final ClienteSmtp correo = new ClienteSmtp("smtp.empresa.example", 587);

    void confirmar(String numero, String correoCliente, long valor) {
        if (valor <= 0) {
            throw new IllegalArgumentException("El valor debe ser positivo");
        }
        repositorio.ejecutarInsert("INSERT INTO pedidos VALUES ('" + numero + "', " + valor + ")");
        correo.enviarMime(correoCliente, "Pedido " + numero + " confirmado", "text/plain");
    }
}

class RepositorioMySql {
    private final String url;
    RepositorioMySql(String url) { this.url = url; }
    void ejecutarInsert(String sql) { System.out.println("[" + url + "] " + sql); }
}

class ClienteSmtp {
    private final String servidor;
    private final int puerto;
    ClienteSmtp(String servidor, int puerto) { this.servidor = servidor; this.puerto = puerto; }
    void enviarMime(String destino, String asunto, String tipo) {
        System.out.println("[" + servidor + ":" + puerto + "] a " + destino + ": " + asunto);
    }
}
