import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.adaptadores.salida.registro.PublicadorEnRegistro;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import registro.RegistroDeEventos;
import registro.RegistroDeEventos.Mensaje;
import vigilancia.adaptadores.entrada.ConsumidorDeTransferencias;
import vigilancia.adaptadores.entrada.ConsumidorDeTransferencias.Lote;
import vigilancia.adaptadores.salida.VigilanciaEnMemoria;
import vigilancia.aplicacion.ServicioDeVigilancia;

/** Compone los dos servicios alrededor del registro y recorre lo que puede salir mal. */
public class Main {

    private static int fallos = 0;

    public static void main(String[] args) {
        RegistroDeEventos registro = new RegistroDeEventos(3);

        // ---- Servicio 1: el banco del módulo hexagonal, sin tocarle una línea ----
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, almacen,
                () -> Instant.parse("2026-11-15T10:00:00Z"), almacen);
        PublicadorEnRegistro haciaElTema = new PublicadorEnRegistro(registro);

        // ---- Servicio 2: vigilancia ----
        List<String> avisos = new ArrayList<>();
        VigilanciaEnMemoria memoria = new VigilanciaEnMemoria();
        ServicioDeVigilancia vigilancia = new ServicioDeVigilancia(memoria, memoria,
                (cuenta, total) -> avisos.add(cuenta + " ha enviado " + total / 100 + " pesos hoy"));
        ConsumidorDeTransferencias consumidor = new ConsumidorDeTransferencias(registro, "vigilancia", vigilancia);

        String ana = banco.abrir("Ana", 100_000_000).id();
        String luis = banco.abrir("Luis", 20_000_000).id();

        System.out.println("1. Tres transferencias; el mensajero vacía la bandeja de salida en el tema");
        banco.transferir(new Orden("orden-1", ana, luis, 30_000_000));
        banco.transferir(new Orden("orden-2", ana, luis, 15_000_000));
        banco.transferir(new Orden("orden-3", luis, ana, 5_000_000));
        comprobar("los tres eventos salen de la bandeja", almacen.entregarPendientes(haciaElTema) == 3);
        List<Mensaje> enElTema = registro.leer("un-lector-cualquiera", PublicadorEnRegistro.TEMA);
        enElTema.forEach(mensaje -> System.out.println("     partición " + mensaje.particion() + " posición "
                + mensaje.posicion() + " clave " + mensaje.clave()));
        comprobar("los dos eventos de Ana están en la misma partición, en orden",
                enElTema.stream().filter(mensaje -> mensaje.clave().equals(ana)).map(Mensaje::particion).distinct().count() == 1);

        System.out.println("2. El consumidor procesa y se cae antes de confirmar las posiciones");
        Lote primero = consumidor.procesarLote(true);
        comprobar("registró las tres", primero.registradas() == 3);
        comprobar("Ana va por 450.000 pesos: todavía no hay aviso", avisos.isEmpty());

        System.out.println("3. Arranca de nuevo: el registro le entrega otra vez lo mismo");
        Lote segundo = consumidor.procesarLote(false);
        comprobar("las tres llegan repetidas y se descartan", segundo.repetidas() == 3 && segundo.registradas() == 0);
        comprobar("sigue sin aviso: la repetición no sumó", avisos.isEmpty());

        System.out.println("4. La misma caída, en otro grupo cuyo caso de uso no recuerda lo que ya vio");
        List<String> avisosFalsos = new ArrayList<>();
        VigilanciaEnMemoria otraMemoria = new VigilanciaEnMemoria();
        ServicioDeVigilancia sinRecuerdo = new ServicioDeVigilancia(otraMemoria, idDelEvento -> true,
                (cuenta, total) -> avisosFalsos.add(cuenta + " ha enviado " + total / 100 + " pesos hoy"));
        ConsumidorDeTransferencias descuidado = new ConsumidorDeTransferencias(registro, "vigilancia-sin-recuerdo", sinRecuerdo);
        descuidado.procesarLote(true);
        descuidado.procesarLote(false);
        avisosFalsos.forEach(aviso -> System.out.println("     aviso falso: " + aviso));
        comprobar("avisa, aunque Ana solo ha enviado 450.000 pesos", avisosFalsos.size() == 1);

        System.out.println("5. Una cuarta transferencia cruza el umbral de verdad");
        banco.transferir(new Orden("orden-4", ana, luis, 10_000_000));
        almacen.entregarPendientes(haciaElTema);
        consumidor.procesarLote(false);
        comprobar("un solo aviso, con el total correcto", avisos.equals(List.of(ana + " ha enviado 550000 pesos hoy")));

        System.out.println("6. Llega un mensaje ilegible al tema");
        registro.publicar(PublicadorEnRegistro.TEMA, ana, "esto no es un evento");
        banco.transferir(new Orden("orden-5", luis, ana, 1_000_000));
        almacen.entregarPendientes(haciaElTema);
        Lote conBasura = consumidor.procesarLote(false);
        comprobar("el ilegible se aparta", conBasura.fallidas() == 1 && consumidor.fallidos().size() == 1);
        comprobar("y el siguiente se procesa", conBasura.registradas() == 1);
        comprobar("no queda nada pendiente para el grupo", consumidor.procesarLote(false).equals(new Lote(0, 0, 0)));

        System.out.println(fallos == 0 ? "Todo correcto" : fallos + " comprobaciones fallaron");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static void comprobar(String que, boolean cumple) {
        System.out.println((cumple ? "   ok    " : "   FALLA ") + que);
        if (!cumple) {
            fallos++;
        }
    }
}
