package banco;

import banco.adaptadores.entrada.consola.Consola;
import banco.adaptadores.entrada.http.ServidorHttp;
import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.adaptadores.salida.sistema.RelojDelSistema;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.salida.Reloj;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** La raíz de composición: el único sitio que conoce todas las piezas y las conecta. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        String modo = args.length > 0 ? args[0] : "consola";

        // 1. Adaptadores de salida.
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        Reloj reloj = new RelojDelSistema();

        // 2. La aplicación. Recibe puertos; no sabe qué hay detrás.
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, almacen, reloj, almacen);

        // 3. El mensajero de la bandeja de salida. Aquí el destino es la pantalla; en producción, un broker.
        Consumer<EventoDeCuenta> destino = evento -> System.out.println("[evento] " + evento.tipo() + " " + evento.id());
        ScheduledExecutorService mensajero = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "mensajero");
            hilo.setDaemon(true);
            return hilo;
        });
        mensajero.scheduleWithFixedDelay(() -> entregar(almacen, destino), 200, 200, TimeUnit.MILLISECONDS);

        // 4. Un adaptador de entrada. Los dos usan los mismos casos de uso.
        switch (modo) {
            case "http" -> {
                int puerto = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
                ServidorHttp servidor = new ServidorHttp(puerto, banco, banco, banco, banco);
                servidor.iniciar();
                System.out.println("Banco en http://localhost:" + servidor.puerto());
            }
            case "consola" -> {
                Consola consola = new Consola(banco, banco, banco, banco, System.out);
                consola.atender(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
                entregar(almacen, destino);
            }
            default -> System.out.println("uso: java -cp salida banco.Main [consola | http [puerto]]");
        }
    }

    private static void entregar(AlmacenEnMemoria almacen, Consumer<EventoDeCuenta> destino) {
        try {
            almacen.entregarPendientes(destino);
        } catch (RuntimeException fallo) {
            System.err.println("entrega de eventos pendiente: " + fallo.getMessage());   // se reintenta en la siguiente vuelta
        }
    }
}
