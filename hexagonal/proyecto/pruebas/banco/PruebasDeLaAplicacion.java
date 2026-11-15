package banco;

import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.aplicacion.puertos.salida.Reloj;
import banco.dominio.ReglaDeNegocioViolada;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Los casos de uso, con adaptadores de memoria. */
public final class PruebasDeLaAplicacion {

    private PruebasDeLaAplicacion() {
    }

    public static void ejecutar() {
        System.out.println("Aplicación:");

        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        List<EventoDeCuenta> eventos = new ArrayList<>();
        Reloj reloj = () -> Instant.parse("2026-11-15T10:00:00Z");
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, almacen, reloj, eventos::add);

        VistaDeCuenta ana = banco.abrir("Ana", 100_000_000);
        VistaDeCuenta luis = banco.abrir("Luis", 20_000_000);

        Resultado resultado = banco.transferir(new Orden("orden-1", ana.id(), luis.id(), 30_000_000));
        Comprobar.igual("la transferencia se realiza", Resultado.REALIZADA, resultado);
        Comprobar.igual("el origen queda con menos", 70_000_000L, saldo(banco, ana.id()));
        Comprobar.igual("el destino queda con más", 50_000_000L, saldo(banco, luis.id()));
        Comprobar.igual("el origen tiene apertura y envío en su extracto", 2, banco.movimientosDe(ana.id()).size());
        Comprobar.igual("se publica un evento", "TransferenciaRealizada", eventos.get(0).tipo());

        Comprobar.lanza("transferir a una cuenta que no existe falla", CuentaNoEncontrada.class,
                () -> banco.transferir(new Orden("orden-2", ana.id(), "cuenta-99", 1_000)));
        Comprobar.lanza("la regla de saldo llega desde el dominio", ReglaDeNegocioViolada.class,
                () -> banco.transferir(new Orden("orden-3", luis.id(), ana.id(), 50_000_001)));
        Comprobar.igual("una transferencia fallida no deja rastro", 70_000_000L, saldo(banco, ana.id()));
        Comprobar.igual("ni publica eventos", 1, eventos.size());

        Comprobar.igual("depositar suma al saldo", 55_000_000L, banco.depositar(luis.id(), 5_000_000).saldoCentavos());
    }

    private static long saldo(ServicioDeCuentas banco, String cuentaId) {
        return banco.porId(cuentaId).orElseThrow().saldoCentavos();
    }
}
