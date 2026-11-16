package tienda;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import tienda.adaptadores.entrada.consola.Consola;
import tienda.adaptadores.entrada.http.ServidorHttp;
import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;
import tienda.adaptadores.salida.memoria.CatalogoEnMemoria;
import tienda.adaptadores.salida.sistema.RelojDelSistema;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.ServicioDePedidos;
import tienda.dominio.Dinero;
import tienda.dominio.Existencias;

/** La raíz de composición: el único sitio que crea adaptadores y los conecta. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        String modo = args.length > 0 ? args[0] : "consola";

        // 1. Adaptadores de salida, con los datos de la tienda del taller 01.
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        almacen.guardar(Existencias.de("teclado", 10));
        almacen.guardar(Existencias.de("raton", 3));
        almacen.guardar(Existencias.de("monitor", 2));
        CatalogoEnMemoria catalogo = new CatalogoEnMemoria(Map.of(
                "teclado", new Dinero(15_000_000), "raton", new Dinero(6_000_000), "monitor", new Dinero(90_000_000)));

        // 2. La aplicación: recibe puertos.
        ServicioDePedidos tienda = new ServicioDePedidos(almacen, catalogo, almacen, almacen, almacen, new RelojDelSistema(), almacen);

        // 3. El mensajero de la bandeja de salida. El "correo" es la pantalla.
        Consumer<EventoDePedido> correo = evento -> System.out.println("[correo a " + evento.cliente() + "] " + evento.tipo() + " " + evento.pedidoId());
        ScheduledExecutorService mensajero = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "mensajero");
            hilo.setDaemon(true);
            return hilo;
        });
        mensajero.scheduleWithFixedDelay(() -> entregar(almacen, correo), 200, 200, TimeUnit.MILLISECONDS);

        // 4. Un adaptador de entrada. Los dos usan los mismos puertos.
        switch (modo) {
            case "http" -> {
                int puerto = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
                ServidorHttp servidor = new ServidorHttp(puerto, tienda, tienda, tienda, tienda, tienda, tienda);
                servidor.iniciar();
                System.out.println("Tienda en http://localhost:" + servidor.puerto());
            }
            case "consola" -> {
                new Consola(tienda, tienda, tienda, tienda, tienda, System.out)
                        .atender(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
                entregar(almacen, correo);
            }
            default -> System.out.println("uso: java -cp salida tienda.Main [consola | http [puerto]]");
        }
    }

    private static void entregar(AlmacenEnMemoria almacen, Consumer<EventoDePedido> destino) {
        try {
            almacen.entregarPendientes(destino);
        } catch (RuntimeException fallo) {
            System.err.println("entrega pendiente: " + fallo.getMessage());
        }
    }
}
