import banco.adaptadores.salida.jdbc.UnidadDeTrabajoJdbc;
import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import fondo.FondoDeConexiones;
import fondo.FondoDeConexiones.SinConexion;
import fondo.UnidadDeTrabajoConFondo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** El mismo banco, con un pool de 4 conexiones debajo de su unidad de trabajo. */
public class Main {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");
    private static final int PETICIONES = 16;
    private static int fallos = 0;

    public static void main(String[] args) {
        System.out.println("1. El evento se entrega a un servicio lento DENTRO de la unidad de trabajo");
        FondoDeConexiones fondoLento = new FondoDeConexiones(4, 300);
        AlmacenEnMemoria almacenLento = new AlmacenEnMemoria();
        PublicadorDeEventos servicioLento = evento -> dormir(200);            // 200 ms con la conexión ocupada
        int[] lento = transferirALaVez(almacenLento, fondoLento, servicioLento);
        System.out.println("     " + lento[0] + " realizadas, " + lento[1] + " sin conexión, en " + lento[2] + " ms");
        comprobar("el pool de 4 se agota: hay peticiones que no consiguen conexión", lento[1] > 0);
        comprobar("y nunca hubo más de 4 en uso", fondoLento.maximoEnUso() == 4);

        System.out.println("2. El mismo caso de uso, con el evento en la bandeja de salida");
        FondoDeConexiones fondoRapido = new FondoDeConexiones(4, 300);
        AlmacenEnMemoria almacenRapido = new AlmacenEnMemoria();
        int[] rapido = transferirALaVez(almacenRapido, fondoRapido, almacenRapido);
        System.out.println("     " + rapido[0] + " realizadas, " + rapido[1] + " sin conexión, en " + rapido[2] + " ms");
        comprobar("las 16 terminan con el mismo pool de 4", rapido[0] == PETICIONES && fondoRapido.agotamientos() == 0);
        AtomicInteger entregados = new AtomicInteger();
        almacenRapido.entregarPendientes(evento -> {
            dormir(20);                                                       // el servicio sigue siendo lento…
            entregados.incrementAndGet();
        });
        comprobar("y los 16 eventos se entregan después, sin ocupar ninguna conexión", entregados.get() == PETICIONES);

        System.out.println("3. Una unidad de trabajo dentro de otra, con un pool de 1");
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        UnidadDeTrabajo pideOtra = new UnidadDeTrabajoConFondo(new FondoDeConexiones(1, 200), almacen, false);
        try {
            pideOtra.ejecutar(() -> pideOtra.ejecutar(() -> "dentro"));
            comprobar("debía quedarse sin conexión", false);
        } catch (SinConexion bloqueo) {
            comprobar("si la de dentro pide otra conexión, espera la que tiene ella misma: " + bloqueo.getMessage(), true);
        }
        UnidadDeTrabajo seUne = new UnidadDeTrabajoConFondo(new FondoDeConexiones(1, 200), almacen, true);
        comprobar("si se une a la abierta, funciona con una sola", seUne.ejecutar(() -> seUne.ejecutar(() -> "dentro")).equals("dentro"));

        System.out.println("4. El adaptador JDBC, con conexiones que anotan lo que se les pide");
        ConexionesDePrueba conexiones = new ConexionesDePrueba();
        UnidadDeTrabajoJdbc jdbc = new UnidadDeTrabajoJdbc(conexiones.pool());
        jdbc.ejecutar(() -> jdbc.ejecutar(() -> "anidada"));
        comprobar("si todo va bien: tomar, confirmar, devolver; y una sola conexión aunque haya anidamiento",
                conexiones.anotado().equals(List.of("tomar", "confirmar", "devolver")) && conexiones.entregadas() == 1);
        try {
            jdbc.ejecutar(() -> {
                throw new IllegalStateException("el caso de uso falló");
            });
        } catch (IllegalStateException esperado) {
            comprobar("si el caso de uso falla: tomar, revertir, devolver",
                    conexiones.anotado().equals(List.of("tomar", "confirmar", "devolver", "tomar", "revertir", "devolver")));
        }

        System.out.println(fallos == 0 ? "Todo correcto" : fallos + " comprobaciones fallaron");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    /** 16 transferencias a la vez entre cuentas distintas. Devuelve realizadas, sin conexión y milisegundos. */
    private static int[] transferirALaVez(AlmacenEnMemoria almacen, FondoDeConexiones fondo, PublicadorDeEventos eventos) {
        UnidadDeTrabajo conFondo = new UnidadDeTrabajoConFondo(fondo, almacen, true);
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, conFondo, RELOJ, eventos);
        List<String> origenes = new ArrayList<>();
        List<String> destinos = new ArrayList<>();
        for (int i = 0; i < PETICIONES; i++) {
            origenes.add(banco.abrir("Origen " + i, 10_000_000).id());
            destinos.add(banco.abrir("Destino " + i, 0).id());
        }
        AtomicInteger realizadas = new AtomicInteger();
        AtomicInteger sinConexion = new AtomicInteger();
        long inicio = System.nanoTime();
        try (ExecutorService peticiones = Executors.newFixedThreadPool(PETICIONES)) {
            for (int i = 0; i < PETICIONES; i++) {
                int numero = i;
                peticiones.submit(() -> {
                    try {
                        banco.transferir(new Orden("orden-" + numero, origenes.get(numero), destinos.get(numero), 1_000_000));
                        realizadas.incrementAndGet();
                    } catch (SinConexion agotado) {
                        sinConexion.incrementAndGet();
                    }
                });
            }
        }
        return new int[] {realizadas.get(), sinConexion.get(), (int) ((System.nanoTime() - inicio) / 1_000_000)};
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }

    private static void comprobar(String que, boolean cumple) {
        System.out.println((cumple ? "   ok    " : "   FALLA ") + que);
        if (!cumple) {
            fallos++;
        }
    }
}
