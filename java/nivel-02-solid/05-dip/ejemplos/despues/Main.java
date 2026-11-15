import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        // Raíz de composición: el único lugar que conoce las clases concretas.
        ServicioPedidos produccion = new ServicioPedidos(
                new RepositorioPedidosMySql("jdbc:mysql://prod-db:3306/tienda"),
                new NotificadorCorreo("smtp.empresa.example", 587));
        produccion.confirmar(new Pedido("P-100", "ana@correo.example", 180_000));

        // La misma regla de negocio, probada sin base de datos ni correo.
        RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
        NotificadorEspia notificador = new NotificadorEspia();
        ServicioPedidos enPrueba = new ServicioPedidos(repositorio, notificador);
        enPrueba.confirmar(new Pedido("P-200", "luis@correo.example", 90_000));
        System.out.println("Guardados: " + repositorio.guardados.size()
                + ", avisos: " + notificador.avisos);
    }
}

// ---------- Negocio (alto nivel) ----------

class Pedido {
    final String numero;
    final String correoCliente;
    final long valor;

    Pedido(String numero, String correoCliente, long valor) {
        if (valor <= 0) {
            throw new IllegalArgumentException("El valor debe ser positivo");
        }
        this.numero = numero;
        this.correoCliente = correoCliente;
        this.valor = valor;
    }
}

/** Lo que el negocio necesita, dicho en su idioma. La interfaz es del negocio. */
interface RepositorioPedidos {
    void guardar(Pedido pedido);
}

interface Notificador {
    void pedidoConfirmado(Pedido pedido);
}

class ServicioPedidos {

    private final RepositorioPedidos repositorio;
    private final Notificador notificador;

    ServicioPedidos(RepositorioPedidos repositorio, Notificador notificador) {
        this.repositorio = repositorio;
        this.notificador = notificador;
    }

    void confirmar(Pedido pedido) {
        repositorio.guardar(pedido);
        notificador.pedidoConfirmado(pedido);
    }
}

// ---------- Infraestructura (bajo nivel) ----------

class RepositorioPedidosMySql implements RepositorioPedidos {
    private final String url;
    RepositorioPedidosMySql(String url) { this.url = url; }

    @Override
    public void guardar(Pedido pedido) {
        System.out.println("[" + url + "] INSERT pedido " + pedido.numero);
    }
}

class NotificadorCorreo implements Notificador {
    private final String servidor;
    private final int puerto;
    NotificadorCorreo(String servidor, int puerto) { this.servidor = servidor; this.puerto = puerto; }

    @Override
    public void pedidoConfirmado(Pedido pedido) {
        System.out.println("[" + servidor + ":" + puerto + "] a " + pedido.correoCliente
                + ": Pedido " + pedido.numero + " confirmado");
    }
}

// ---------- Dobles para pruebas ----------

class RepositorioEnMemoria implements RepositorioPedidos {
    final List<Pedido> guardados = new ArrayList<>();
    @Override public void guardar(Pedido pedido) { guardados.add(pedido); }
}

class NotificadorEspia implements Notificador {
    final List<String> avisos = new ArrayList<>();
    @Override public void pedidoConfirmado(Pedido pedido) { avisos.add(pedido.numero); }
}
