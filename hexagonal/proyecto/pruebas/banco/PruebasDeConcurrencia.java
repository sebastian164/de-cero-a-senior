package banco;

import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** La actualización perdida, el bloqueo optimista, el reintento y una prueba con muchos hilos. */
public final class PruebasDeConcurrencia {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");
    private static final LocalDate HOY = LocalDate.parse("2026-11-15");

    private PruebasDeConcurrencia() {
    }

    public static void ejecutar() {
        System.out.println("Concurrencia, sin control de versión:");
        Comprobar.igual("la segunda consignación pisa a la primera: se pierden 100.000 pesos",
                105_000_000L, dosCajerosALaVez(new RepositorioIngenuo()));

        System.out.println("Concurrencia, con control de versión:");
        Comprobar.lanza("el segundo cajero recibe un conflicto en lugar de pisar al primero",
                ConflictoDeConcurrencia.class, () -> dosCajerosALaVez(new AlmacenEnMemoria()));
        elCasoDeUsoReintenta();
        elCasoDeUsoSeRinde();
        muchosHilos();
    }

    /** Dos cajeros leen la misma cuenta, cada uno consigna y cada uno guarda. Devuelve el saldo final. */
    private static long dosCajerosALaVez(RepositorioDeCuentas cuentas) {
        String id = cuentas.siguienteId();
        cuentas.guardar(Cuenta.abrir(id, "Ana", Dinero.pesos(1_000_000), HOY));

        Cuenta laDelCajero1 = cuentas.buscar(id).orElseThrow();  // los dos leen 1.000.000
        Cuenta laDelCajero2 = cuentas.buscar(id).orElseThrow();

        laDelCajero1.depositar(Dinero.pesos(100_000));
        cuentas.guardar(laDelCajero1);                           // queda 1.100.000

        laDelCajero2.depositar(Dinero.pesos(50_000));
        cuentas.guardar(laDelCajero2);                           // 1.050.000 encima, o conflicto

        return cuentas.buscar(id).orElseThrow().saldo().centavos();
    }

    private static void elCasoDeUsoReintenta() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        RepositorioConInterferencia cuentas = new RepositorioConInterferencia(almacen);
        ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, almacen, almacen, almacen, RELOJ, almacen);
        String ana = banco.abrir("Ana", 100_000_000).id();
        String luis = banco.abrir("Luis", 20_000_000).id();

        // Mientras la transferencia tiene la cuenta de Ana en la mano, otro le consigna 50.000 pesos.
        cuentas.interferir(1, () -> banco.depositar(ana, 5_000_000));
        Resultado resultado = banco.transferir(new Orden("orden-1", ana, luis, 30_000_000));

        Comprobar.igual("la transferencia termina bien", Resultado.REALIZADA, resultado);
        Comprobar.igual("necesitó dos intentos: cuatro lecturas en lugar de dos", 4, cuentas.lecturas());
        Comprobar.igual("no se perdió la consignación ni la transferencia", 75_000_000L, saldo(banco, ana));
        Comprobar.igual("Luis recibió una sola vez", 50_000_000L, saldo(banco, luis));
        Comprobar.igual("y hay un solo envío en el extracto de Ana", 3, banco.movimientosDe(ana).size());
    }

    private static void elCasoDeUsoSeRinde() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        RepositorioConInterferencia cuentas = new RepositorioConInterferencia(almacen);
        ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, almacen, almacen, almacen, RELOJ, almacen);
        String ana = banco.abrir("Ana", 100_000_000).id();
        String luis = banco.abrir("Luis", 20_000_000).id();

        // Alguien cambia la cuenta de Ana en cada lectura: el conflicto no cede.
        cuentas.interferir(100, () -> banco.depositar(ana, 1_000_000));
        Comprobar.lanza("tras tres intentos, el conflicto llega a quien llamó", ConflictoDeConcurrencia.class,
                () -> banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        cuentas.interferir(0, null);

        Comprobar.igual("a Luis no le llegó nada", 20_000_000L, saldo(banco, luis));
        Comprobar.igual("Ana solo tiene la apertura y las consignaciones del otro", 0L,
                banco.movimientosDe(ana).stream().filter(movimiento -> movimiento.referencia().equals("orden-1")).count());
        Comprobar.igual("y la orden se puede volver a enviar", Resultado.REALIZADA,
                banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));
    }

    private static void muchosHilos() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, almacen, RELOJ, almacen);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(banco.abrir("Titular " + i, 50_000_000).id());
        }
        int hilos = 8;
        int porHilo = 200;
        AtomicInteger realizadas = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();
        AtomicInteger otrosErrores = new AtomicInteger();

        try (ExecutorService grupo = Executors.newFixedThreadPool(hilos)) {
            for (int h = 0; h < hilos; h++) {
                int hilo = h;
                grupo.submit(() -> {
                    for (int i = 0; i < porHilo; i++) {
                        String origen = ids.get((hilo + i) % 4);
                        String destino = ids.get((hilo + i + 1 + i % 3) % 4);
                        try {
                            banco.transferir(new Orden("hilo-" + hilo + "-" + i, origen, destino, 10_000));
                            realizadas.incrementAndGet();
                        } catch (ConflictoDeConcurrencia conflicto) {
                            conflictos.incrementAndGet();
                        } catch (RuntimeException error) {
                            otrosErrores.incrementAndGet();
                        }
                    }
                });
            }
        }                                                        // al cerrar, espera a que terminen todos

        long total = ids.stream().mapToLong(id -> saldo(banco, id)).sum();
        long renglones = ids.stream().mapToLong(id -> banco.movimientosDe(id).size()).sum();
        System.out.println("  (" + realizadas + " realizadas, " + conflictos + " rendidas por conflicto)");
        Comprobar.igual("con 8 hilos y 1.600 transferencias, el dinero total es el mismo", 200_000_000L, total);
        Comprobar.igual("cada transferencia se hizo entera o no se hizo", hilos * porHilo, realizadas.get() + conflictos.get());
        Comprobar.igual("ningún otro error", 0, otrosErrores.get());
        Comprobar.igual("el extracto cuadra: dos renglones por transferencia realizada", 4L + 2L * realizadas.get(), renglones);
    }

    private static long saldo(ServicioDeCuentas banco, String cuentaId) {
        return banco.porId(cuentaId).orElseThrow().saldoCentavos();
    }
}
