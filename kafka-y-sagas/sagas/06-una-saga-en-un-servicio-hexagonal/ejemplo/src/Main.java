import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.ServicioDeCuentas;
import banco.dominio.Movimiento;
import banco.dominio.ReglaDeNegocioViolada;
import interbancaria.adaptadores.EnviosEnMemoria;
import interbancaria.adaptadores.RedSimulada;
import interbancaria.aplicacion.EnviarAOtroBanco.Orden;
import interbancaria.aplicacion.Envio.Estado;
import interbancaria.aplicacion.ServicioDeEnvios;
import java.time.Instant;
import java.util.List;

/** El banco del módulo hexagonal, más una saga para enviar dinero a otro banco. */
public class Main {

    private static int fallos = 0;

    public static void main(String[] args) {
        // ---- Composición: los mismos adaptadores del banco, más los de la saga ----
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        EnviosEnMemoria sagas = new EnviosEnMemoria();
        RedSimulada red = new RedSimulada();
        red.existe("cuenta-de-marta");
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, almacen,
                () -> Instant.parse("2026-11-15T10:00:00Z"), almacen);
        ServicioDeEnvios envios = new ServicioDeEnvios(almacen, almacen, sagas, red, sagas.juntoCon(almacen),
                () -> Instant.parse("2026-11-15T10:00:00Z"));

        String ana = banco.abrir("Ana", 100_000_000).id();       // 1.000.000 de pesos

        System.out.println("1. El otro banco acepta");
        Estado uno = envios.enviar(new Orden("envio-1", ana, "otro-banco", "cuenta-de-marta", 20_000_000));
        comprobar("el envío queda completado", uno == Estado.COMPLETADO);
        comprobar("a Ana le salieron 200.000 pesos", saldo(banco, ana) == 80_000_000);

        System.out.println("2. El otro banco rechaza: la cuenta de destino no existe");
        Estado dos = envios.enviar(new Orden("envio-2", ana, "otro-banco", "cuenta-inventada", 15_000_000));
        comprobar("el envío queda devuelto", dos == Estado.DEVUELTO);
        comprobar("el saldo de Ana vuelve a ser el de antes", saldo(banco, ana) == 80_000_000);
        List<Movimiento> delEnvio2 = banco.movimientosDe(ana).stream()
                .filter(movimiento -> movimiento.referencia().contains("envio-2")).toList();
        delEnvio2.forEach(movimiento -> System.out.println("     " + movimiento.tipo() + " " + movimiento.valor().centavos() / 100
                + " (" + movimiento.referencia() + ")"));
        comprobar("en el extracto quedan los dos renglones: compensar no es borrar", delEnvio2.size() == 2);

        System.out.println("3. El otro banco acepta, pero la respuesta se pierde");
        red.perderRespuestas(1);
        Estado tres = envios.enviar(new Orden("envio-3", ana, "otro-banco", "cuenta-de-marta", 10_000_000));
        comprobar("el envío queda abierto: debitado y sin cerrar", tres == Estado.DEBITADO);
        comprobar("el dinero salió de la cuenta de Ana", saldo(banco, ana) == 70_000_000);
        comprobar("y sí llegó al otro banco, aunque aquí no se sepa", red.recibidoCentavos() == 30_000_000);
        comprobar("no se devolvió: habría quedado en los dos lados", saldo(banco, ana) + red.recibidoCentavos() == 100_000_000);
        comprobar("al reanudar, se cierra", envios.reanudarPendientes() == 1);
        comprobar("y el otro banco no abonó dos veces", red.recibidoCentavos() == 30_000_000);

        System.out.println("4. Nuestro proceso muere después de debitar y antes de llamar");
        red.matarElProceso(1);
        try {
            envios.enviar(new Orden("envio-4", ana, "otro-banco", "cuenta-de-marta", 10_000_000));
            comprobar("el proceso debía morir", false);
        } catch (IllegalStateException caida) {
            comprobar("la llamada no llegó a hacerse", red.recibidoCentavos() == 30_000_000);
        }
        comprobar("el débito quedó confirmado junto con la saga", saldo(banco, ana) == 60_000_000);
        comprobar("al arrancar de nuevo, la saga se retoma y se completa", envios.reanudarPendientes() == 1);
        comprobar("ahora sí llegó", red.recibidoCentavos() == 40_000_000);

        System.out.println("5. El cliente repite una orden que ya se completó");
        int llamadasAntes = red.llamadas();
        Estado repetida = envios.enviar(new Orden("envio-1", ana, "otro-banco", "cuenta-de-marta", 20_000_000));
        comprobar("responde con el estado que tenía", repetida == Estado.COMPLETADO);
        comprobar("sin debitar de nuevo ni llamar a la red", saldo(banco, ana) == 60_000_000 && red.llamadas() == llamadasAntes);

        System.out.println("6. Una regla del dominio corta la saga antes de empezar");
        try {
            envios.enviar(new Orden("envio-5", ana, "otro-banco", "cuenta-de-marta", 30_000_000));
            comprobar("debía rechazarse", false);
        } catch (ReglaDeNegocioViolada rechazo) {
            System.out.println("     " + rechazo.getMessage());
            comprobar("no queda ninguna saga abierta", envios.reanudarPendientes() == 0);
        }

        System.out.println("7. La invariante");
        comprobar("lo que tiene Ana más lo que recibió el otro banco es lo que había al principio",
                saldo(banco, ana) + red.recibidoCentavos() == 100_000_000);

        System.out.println(fallos == 0 ? "Todo correcto" : fallos + " comprobaciones fallaron");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static long saldo(ServicioDeCuentas banco, String cuentaId) {
        return banco.porId(cuentaId).orElseThrow().saldoCentavos();
    }

    private static void comprobar(String que, boolean cumple) {
        System.out.println((cumple ? "   ok    " : "   FALLA ") + que);
        if (!cumple) {
            fallos++;
        }
    }
}
