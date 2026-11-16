import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;
import tienda.adaptadores.salida.memoria.CatalogoEnMemoria;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.puertos.entrada.CrearPedido.Linea;
import tienda.aplicacion.puertos.entrada.CrearPedido.Solicitud;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.dominio.Dinero;
import tienda.dominio.Existencias;

/**
 * Paso 1 del taller 05: queda un monitor y dos pedidos lo leen antes de que ninguno guarde.
 * Se compila contra la solución que se le indique (la de la sesión 04 o la de la 05) y cuenta qué pasó.
 */
public class Paso1 {

    public static void main(String[] args) {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        almacen.guardar(Existencias.de("monitor", 1));
        Runnable[] otro = new Runnable[1];
        boolean[] interferido = new boolean[1];
        Inventario conInterferencia = new Inventario() {
            @Override
            public Optional<Existencias> existenciasDe(String producto) {
                Optional<Existencias> leidas = almacen.existenciasDe(producto);
                if (!interferido[0] && !Thread.currentThread().getName().equals("el-otro")) {
                    interferido[0] = true;
                    Thread hilo = new Thread(otro[0], "el-otro");
                    hilo.start();
                    try {
                        hilo.join();
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
                return leidas;
            }

            @Override
            public void guardar(Existencias existencias) {
                almacen.guardar(existencias);
            }
        };
        ServicioDePedidos tienda = new ServicioDePedidos(almacen, new CatalogoEnMemoria(Map.of("monitor", new Dinero(90_000_000))),
                conInterferencia, almacen, almacen, () -> Instant.parse("2026-11-15T10:00:00Z"), almacen);
        otro[0] = () -> System.out.println("luis: " + tienda.crear(new Solicitud("k-luis", "luis", List.of(new Linea("monitor", 1)))).pedido().estado());
        try {
            System.out.println("ana:  " + tienda.crear(new Solicitud("k-ana", "ana", List.of(new Linea("monitor", 1)))).pedido().estado());
        } catch (RuntimeException rechazo) {
            System.out.println("ana:  rechazada (" + rechazo.getClass().getSimpleName() + ": " + rechazo.getMessage() + ")");
        }
        int vendidas = tienda.deCliente("ana").size() + tienda.deCliente("luis").size();
        int quedan = almacen.existenciasDe("monitor").orElseThrow().unidades();
        System.out.println("vendidas=" + vendidas + " quedan=" + quedan + " vendidas+quedan=" + (vendidas + quedan) + " (iniciales 1)");
        System.exit(args.length > 0 && Integer.parseInt(args[0]) != vendidas ? 1 : 0);
    }
}
