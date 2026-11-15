package banco;

import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Atomicidad, idempotencia y bandeja de salida, provocando el fallo a voluntad. */
public final class PruebasDeTransacciones {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");

    private PruebasDeTransacciones() {
    }

    public static void ejecutar() {
        System.out.println("Transacciones, sin unidad de trabajo:");
        sinTransaccion();
        System.out.println("Transacciones, con unidad de trabajo:");
        conTransaccion();
        System.out.println("Bandeja de salida:");
        bandejaDeSalida();
    }

    private static void sinTransaccion() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        RepositorioQueFalla cuentas = new RepositorioQueFalla(almacen);
        UnidadDeTrabajo ninguna = new SinTransaccion();
        ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, almacen, almacen, ninguna, RELOJ, almacen);
        String ana = banco.abrir("Ana", 100_000_000).id();
        String luis = banco.abrir("Luis", 20_000_000).id();

        cuentas.fallarAlGuardar(luis);                           // el fallo ocurre entre el débito y el abono
        Comprobar.lanza("la transferencia falla a mitad", IllegalStateException.class,
                () -> banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));

        Comprobar.igual("a Ana sí le salió el dinero", 70_000_000L, saldo(banco, ana));
        Comprobar.igual("a Luis no le llegó", 20_000_000L, saldo(banco, luis));
        Comprobar.igual("faltan 300.000 pesos en el banco", 90_000_000L, saldo(banco, ana) + saldo(banco, luis));

        cuentas.reparar();
        Comprobar.igual("y reintentar no lo arregla: la orden figura como atendida", Resultado.REPETIDA,
                banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));
    }

    private static void conTransaccion() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        RepositorioQueFalla cuentas = new RepositorioQueFalla(almacen);
        ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, almacen, almacen, almacen, RELOJ, almacen);
        String ana = banco.abrir("Ana", 100_000_000).id();
        String luis = banco.abrir("Luis", 20_000_000).id();

        cuentas.fallarAlGuardar(luis);
        Comprobar.lanza("la transferencia falla a mitad", IllegalStateException.class,
                () -> banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));

        Comprobar.igual("a Ana no le salió nada", 100_000_000L, saldo(banco, ana));
        Comprobar.igual("el dinero total no cambió", 120_000_000L, saldo(banco, ana) + saldo(banco, luis));
        Comprobar.igual("el extracto de Ana solo tiene la apertura", 1, banco.movimientosDe(ana).size());

        cuentas.reparar();
        Comprobar.igual("la misma orden se puede reintentar", Resultado.REALIZADA,
                banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("y enviarla otra vez no transfiere de nuevo", Resultado.REPETIDA,
                banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("Ana pagó una sola vez", 70_000_000L, saldo(banco, ana));
        Comprobar.igual("Luis recibió una sola vez", 50_000_000L, saldo(banco, luis));
    }

    private static void bandejaDeSalida() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        RepositorioQueFalla cuentas = new RepositorioQueFalla(almacen);
        ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, almacen, almacen, almacen, RELOJ, almacen);
        String ana = banco.abrir("Ana", 100_000_000).id();
        String luis = banco.abrir("Luis", 20_000_000).id();
        List<EventoDeCuenta> recibidos = new ArrayList<>();

        cuentas.fallarAlGuardar(luis);
        Comprobar.lanza("una transferencia que falla", IllegalStateException.class,
                () -> banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("no deja ningún evento por entregar", 0, almacen.entregarPendientes(recibidos::add));

        cuentas.reparar();
        banco.transferir(new Orden("orden-1", ana, luis, 30_000_000));
        Comprobar.lanza("si el destino de los eventos está caído, la entrega falla", IllegalStateException.class,
                () -> almacen.entregarPendientes(evento -> {
                    throw new IllegalStateException("broker no disponible");
                }));
        Comprobar.igual("pero el evento no se pierde: sale en el siguiente intento", 1, almacen.entregarPendientes(recibidos::add));
        Comprobar.igual("y lleva un id estable para que el receptor descarte repeticiones", "transferencia:orden-1", recibidos.get(0).id());
        Comprobar.igual("no se entrega dos veces si no hace falta", 0, almacen.entregarPendientes(recibidos::add));
    }

    private static long saldo(ServicioDeCuentas banco, String cuentaId) {
        return banco.porId(cuentaId).orElseThrow().saldoCentavos();
    }
}
