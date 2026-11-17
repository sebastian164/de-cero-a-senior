import banco.Comprobar;
import banco.ContratoDelRepositorio;
import banco.RepositorioQueFalla;
import banco.adaptadores.salida.mongo.CuentasMongo;
import banco.adaptadores.salida.mongo.MovimientosMongo;
import banco.adaptadores.salida.mongo.OrdenesMongo;
import banco.adaptadores.salida.mongo.UnidadDeTrabajoMongo;
import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.aplicacion.puertos.salida.Reloj;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * El banco con un primer adaptador de MongoDB. Abrir, depositar, transferir y consultar funcionan;
 * por consola o por HTTP, con un usuario, no se nota nada raro. Las mismas comprobaciones que pasa el adaptador bueno:
 */
public class Main {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");

    public static void main(String[] args) {
        try (MongoClient cliente = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            contrato(cliente);
            atomicidad(cliente);
            laMismaOrdenALaVez(cliente);
            muchasTransferenciasALaVez(cliente);
        }
        System.out.println();
        System.out.println(Comprobar.total() + " comprobaciones, " + Comprobar.fallos() + " fallos");
    }

    private record Montaje(ServicioDeCuentas banco, RepositorioQueFalla cuentas, MovimientosMongo movimientos,
                           List<EventoDeCuenta> eventos) {

        static Montaje sobre(MongoClient cliente, String nombreDeLaBase) {
            MongoDatabase base = cliente.getDatabase(nombreDeLaBase);
            base.drop();
            RepositorioQueFalla cuentas = new RepositorioQueFalla(new CuentasMongo(base));
            MovimientosMongo movimientos = new MovimientosMongo(base);
            List<EventoDeCuenta> eventos = Collections.synchronizedList(new ArrayList<>());
            ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, movimientos, new OrdenesMongo(base),
                    new UnidadDeTrabajoMongo(), RELOJ, eventos::add);       // los eventos salen directo, sin bandeja
            return new Montaje(banco, cuentas, movimientos, eventos);
        }
    }

    private static void contrato(MongoClient cliente) {
        System.out.println("1. El contrato del repositorio, el mismo que pasa el almacén en memoria:");
        MongoDatabase base = cliente.getDatabase("nosql04_antes_contrato");
        base.drop();
        Comprobar.igual("CuentasMongo lo cumple entero", List.of(), ContratoDelRepositorio.incumplimientos(() -> new CuentasMongo(base)));
    }

    private static void atomicidad(MongoClient cliente) {
        System.out.println("2. Una transferencia que falla a mitad:");
        Montaje m = Montaje.sobre(cliente, "nosql04_antes_atomicidad");
        String ana = m.banco().abrir("Ana", 100_000_000).id();
        String luis = m.banco().abrir("Luis", 20_000_000).id();

        m.cuentas().fallarAlGuardar(luis);
        Comprobar.lanza("la transferencia falla", IllegalStateException.class,
                () -> m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("a Ana no le salió nada", 100_000_000L, saldo(m, ana));
        Comprobar.igual("el extracto de Ana solo tiene la apertura", 1, m.movimientos().de(ana).size());

        m.cuentas().reparar();
        Comprobar.igual("la misma orden se puede reintentar", Resultado.REALIZADA,
                m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("Luis recibió los 300.000 pesos", 50_000_000L, saldo(m, luis));
    }

    private static void laMismaOrdenALaVez(MongoClient cliente) {
        System.out.println("3. La misma orden, enviada por 8 hilos a la vez:");
        Montaje m = Montaje.sobre(cliente, "nosql04_antes_idempotencia");
        String ana = m.banco().abrir("Ana", 100_000_000).id();
        String luis = m.banco().abrir("Luis", 0).id();
        AtomicInteger realizadas = new AtomicInteger();
        enParalelo(8, 1, i -> {
            if (m.banco().transferir(new Orden("orden-repetida", ana, luis, 1_000_000)) == Resultado.REALIZADA) {
                realizadas.incrementAndGet();
            }
        });
        Comprobar.igual("se realiza exactamente una vez", 1, realizadas.get());
        Comprobar.igual("y Ana paga exactamente una vez", 99_000_000L, saldo(m, ana));
    }

    private static void muchasTransferenciasALaVez(MongoClient cliente) {
        System.out.println("4. 200 transferencias a la vez entre 4 cuentas, con 8 hilos:");
        Montaje m = Montaje.sobre(cliente, "nosql04_antes_concurrencia");
        List<String> cuentas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cuentas.add(m.banco().abrir("Titular " + i, 100_000_000).id());
        }
        AtomicInteger realizadas = new AtomicInteger();
        AtomicInteger rendidas = new AtomicInteger();
        enParalelo(8, 25, i -> {
            String origen = cuentas.get(i % 4);
            String destino = cuentas.get((i + 1 + (i / 4) % 3) % 4);
            try {
                m.banco().transferir(new Orden("orden-" + i, origen, destino, 100_000));
                realizadas.incrementAndGet();
            } catch (ConflictoDeConcurrencia conflicto) {
                rendidas.incrementAndGet();
            }
        });
        long total = cuentas.stream().mapToLong(cuenta -> saldo(m, cuenta)).sum();
        System.out.println("     realizadas " + realizadas.get() + ", abandonadas tras tres conflictos " + rendidas.get());
        Comprobar.igual("el dinero total no cambia", 400_000_000L, total);
    }

    private static long saldo(Montaje m, String cuentaId) {
        return m.banco().porId(cuentaId).orElseThrow().saldoCentavos();
    }

    private interface Tarea {

        void ejecutar(int numero);
    }

    private static void enParalelo(int hilos, int vecesPorHilo, Tarea tarea) {
        List<Future<?>> enviadas = new ArrayList<>();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(hilos)) {
            for (int h = 0; h < hilos; h++) {
                int hilo = h;
                enviadas.add(ejecutor.submit(() -> {
                    for (int v = 0; v < vecesPorHilo; v++) {
                        tarea.ejecutar(hilo * vecesPorHilo + v);
                    }
                }));
            }
        }
        for (Future<?> enviada : enviadas) {
            try {
                enviada.get();
            } catch (InterruptedException | ExecutionException fallo) {
                throw new IllegalStateException(fallo);
            }
        }
    }
}
