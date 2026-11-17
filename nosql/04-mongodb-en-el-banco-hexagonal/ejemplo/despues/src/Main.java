import banco.Comprobar;
import banco.ContratoDelRepositorio;
import banco.RepositorioQueFalla;
import banco.adaptadores.salida.mongo.BandejaDeSalidaMongo;
import banco.adaptadores.salida.mongo.CuentasMongo;
import banco.adaptadores.salida.mongo.EsquemaMongo;
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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** El banco del módulo hexagonal, sin cambiar una línea de su núcleo, guardando en MongoDB. */
public class Main {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");

    public static void main(String[] args) {
        try (MongoClient cliente = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            contrato(cliente);
            atomicidad(cliente);
            laMismaOrdenALaVez(cliente);
            muchasTransferenciasALaVez(cliente);
            bandejaDeSalida(cliente);
        }
        System.out.println();
        System.out.println(Comprobar.total() + " comprobaciones, " + Comprobar.fallos() + " fallos");
        if (Comprobar.fallos() > 0) {
            System.exit(1);
        }
    }

    /** La raíz de composición: los mismos puertos de siempre, con adaptadores de MongoDB. */
    private record Montaje(ServicioDeCuentas banco, RepositorioQueFalla cuentas, MovimientosMongo movimientos,
                           BandejaDeSalidaMongo bandeja) {

        static Montaje sobre(MongoClient cliente, String nombreDeLaBase) {
            MongoDatabase base = cliente.getDatabase(nombreDeLaBase);
            base.drop();
            EsquemaMongo.preparar(base);
            UnidadDeTrabajoMongo transacciones = new UnidadDeTrabajoMongo(cliente);
            RepositorioQueFalla cuentas = new RepositorioQueFalla(new CuentasMongo(base, transacciones));
            MovimientosMongo movimientos = new MovimientosMongo(base, transacciones);
            BandejaDeSalidaMongo bandeja = new BandejaDeSalidaMongo(base, transacciones);
            ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, movimientos, new OrdenesMongo(base, transacciones),
                    transacciones, RELOJ, bandeja);
            return new Montaje(banco, cuentas, movimientos, bandeja);
        }
    }

    private static void contrato(MongoClient cliente) {
        System.out.println("1. El contrato del repositorio, el mismo que pasa el almacén en memoria:");
        MongoDatabase base = cliente.getDatabase("nosql04_despues_contrato");
        base.drop();
        EsquemaMongo.preparar(base);
        UnidadDeTrabajoMongo transacciones = new UnidadDeTrabajoMongo(cliente);
        Comprobar.igual("CuentasMongo lo cumple entero", List.of(),
                ContratoDelRepositorio.incumplimientos(() -> new CuentasMongo(base, transacciones)));
    }

    private static void atomicidad(MongoClient cliente) {
        System.out.println("2. Una transferencia que falla a mitad:");
        Montaje m = Montaje.sobre(cliente, "nosql04_despues_atomicidad");
        String ana = m.banco().abrir("Ana", 100_000_000).id();
        String luis = m.banco().abrir("Luis", 20_000_000).id();

        m.cuentas().fallarAlGuardar(luis);                       // el fallo ocurre entre el débito y el abono
        Comprobar.lanza("la transferencia falla", IllegalStateException.class,
                () -> m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("a Ana no le salió nada", 100_000_000L, saldo(m, ana));
        Comprobar.igual("el extracto de Ana solo tiene la apertura", 1, m.movimientos().de(ana).size());

        m.cuentas().reparar();
        Comprobar.igual("la misma orden se puede reintentar", Resultado.REALIZADA,
                m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("y enviarla otra vez no transfiere de nuevo", Resultado.REPETIDA,
                m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("Ana pagó una sola vez", 70_000_000L, saldo(m, ana));
        Comprobar.igual("Luis recibió una sola vez", 50_000_000L, saldo(m, luis));
    }

    private static void laMismaOrdenALaVez(MongoClient cliente) {
        System.out.println("3. La misma orden, enviada por 8 hilos a la vez:");
        Montaje m = Montaje.sobre(cliente, "nosql04_despues_idempotencia");
        String ana = m.banco().abrir("Ana", 100_000_000).id();
        String luis = m.banco().abrir("Luis", 0).id();
        AtomicInteger realizadas = new AtomicInteger();
        enParalelo(8, 1, i -> {
            try {
                if (m.banco().transferir(new Orden("orden-repetida", ana, luis, 1_000_000)) == Resultado.REALIZADA) {
                    realizadas.incrementAndGet();
                }
            } catch (ConflictoDeConcurrencia rendida) {
                // tras tres intentos, el caso de uso se rinde: no transfirió nada
            }
        });
        Comprobar.igual("se realiza exactamente una vez", 1, realizadas.get());
        Comprobar.igual("y Ana paga exactamente una vez", 99_000_000L, saldo(m, ana));
    }

    private static void muchasTransferenciasALaVez(MongoClient cliente) {
        System.out.println("4. 200 transferencias a la vez entre 4 cuentas, con 8 hilos:");
        Montaje m = Montaje.sobre(cliente, "nosql04_despues_concurrencia");
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
        int movimientos = cuentas.stream().mapToInt(cuenta -> m.movimientos().de(cuenta).size()).sum();
        System.out.println("     realizadas " + realizadas.get() + ", abandonadas tras tres conflictos " + rendidas.get());
        Comprobar.igual("ninguna se pierde: o se realizó o se abandonó entera", 200, realizadas.get() + rendidas.get());
        Comprobar.igual("el dinero total no cambia", 400_000_000L, total);
        Comprobar.igual("cada transferencia realizada dejó dos movimientos", 4 + 2 * realizadas.get(), movimientos);
        Comprobar.igual("y un evento en la bandeja de salida", realizadas.get(), m.bandeja().entregarPendientes(evento -> { }));
    }

    private static void bandejaDeSalida(MongoClient cliente) {
        System.out.println("5. La bandeja de salida:");
        Montaje m = Montaje.sobre(cliente, "nosql04_despues_bandeja");
        String ana = m.banco().abrir("Ana", 100_000_000).id();
        String luis = m.banco().abrir("Luis", 20_000_000).id();
        List<EventoDeCuenta> recibidos = new ArrayList<>();

        m.cuentas().fallarAlGuardar(luis);
        Comprobar.lanza("una transferencia que falla", IllegalStateException.class,
                () -> m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("no deja ningún evento por entregar", 0, m.bandeja().entregarPendientes(recibidos::add));

        m.cuentas().reparar();
        m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000));
        Comprobar.lanza("si el destino de los eventos está caído, la entrega falla", IllegalStateException.class,
                () -> m.bandeja().entregarPendientes(evento -> {
                    throw new IllegalStateException("broker no disponible");
                }));
        Comprobar.igual("pero el evento no se pierde: sale en el siguiente intento", 1, m.bandeja().entregarPendientes(recibidos::add));
        Comprobar.igual("con un id estable para que el receptor descarte repeticiones", "transferencia:orden-1", recibidos.get(0).id());
    }

    private static long saldo(Montaje m, String cuentaId) {
        return m.banco().porId(cuentaId).orElseThrow().saldoCentavos();
    }

    /** Una tarea numerada, para que cada hilo sepa qué orden le toca. */
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
