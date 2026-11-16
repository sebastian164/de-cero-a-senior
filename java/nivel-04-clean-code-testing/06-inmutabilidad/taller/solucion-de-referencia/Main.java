import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Solución del taller 06. Ejecutar con: java Main.java (termina con código 1 si alguna comprobación falla). */
public class Main {

    private static int fallos = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("1. Periodo");
        punto1Periodo();
        System.out.println("2. Email y Cedula");
        punto2ObjetosDeValor();
        System.out.println("3. De mutable a inmutable");
        punto3Pedido();
        System.out.println("4. Dos hilos");
        punto4Hilos();
        System.out.println("5. Clave mutable en un HashMap");
        punto5ClaveMutable();
        System.out.println(fallos == 0 ? "TODO PASA" : fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1
    static void punto1Periodo() {
        LocalDate enero1 = LocalDate.of(2026, 1, 1);
        LocalDate enero31 = LocalDate.of(2026, 1, 31);
        Periodo enero = new Periodo(enero1, enero31);

        comprobar("contiene el primer día", enero.contiene(enero1));
        comprobar("contiene el último día", enero.contiene(enero31));
        comprobar("no contiene el día anterior", !enero.contiene(enero1.minusDays(1)));
        comprobar("no contiene el día siguiente", !enero.contiene(enero31.plusDays(1)));
        comprobar("un periodo de un día es válido", new Periodo(enero1, enero1).contiene(enero1));
        comprobar("fin anterior a inicio se rechaza", lanza(IllegalArgumentException.class, () -> new Periodo(enero31, enero1)));
        comprobar("null se rechaza", lanza(NullPointerException.class, () -> new Periodo(null, enero1)));

        Periodo febrero = new Periodo(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        Periodo quincena = new Periodo(LocalDate.of(2026, 1, 20), LocalDate.of(2026, 2, 5));
        Periodo tocaElUltimoDia = new Periodo(enero31, LocalDate.of(2026, 3, 1));
        comprobar("enero y febrero no se solapan", !enero.seSolapaCon(febrero) && !febrero.seSolapaCon(enero));
        comprobar("una quincena que cruza el mes se solapa con ambos", enero.seSolapaCon(quincena) && febrero.seSolapaCon(quincena));
        comprobar("compartir solo el último día es solaparse", enero.seSolapaCon(tocaElUltimoDia));
        comprobar("un periodo se solapa consigo mismo", enero.seSolapaCon(enero));

        Periodo extendido = enero.extendidoHasta(LocalDate.of(2026, 2, 15));
        comprobar("extendidoHasta devuelve otro periodo", extendido.fin().equals(LocalDate.of(2026, 2, 15)) && extendido.inicio().equals(enero1));
        comprobar("el original no cambia", enero.fin().equals(enero31));
        comprobar("extender hacia atrás se rechaza", lanza(IllegalArgumentException.class, () -> enero.extendidoHasta(LocalDate.of(2026, 1, 10))));
        comprobar("equals y hashCode por valor", enero.equals(new Periodo(enero1, enero31)) && enero.hashCode() == new Periodo(enero1, enero31).hashCode());
    }

    // ------------------------------------------------------------------ 2
    static void punto2ObjetosDeValor() throws Exception {
        comprobar("el correo se normaliza", new Email("  Ana@Correo.Example ").valor().equals("ana@correo.example"));
        comprobar("dos correos iguales salvo mayúsculas son iguales", new Email("ANA@correo.example").equals(new Email("ana@correo.example")));
        comprobar("correo sin arroba se rechaza", lanza(IllegalArgumentException.class, () -> new Email("ana.correo.example")));
        comprobar("la cédula ignora los puntos", new Cedula("1.020.304.050").valor().equals("1020304050"));
        comprobar("cédula con letras se rechaza", lanza(IllegalArgumentException.class, () -> new Cedula("10A0")));
        comprobar("cédula demasiado corta se rechaza", lanza(IllegalArgumentException.class, () -> new Cedula("12345")));

        // Antes: registrar(String email, String cedula). Invertir los argumentos compila.
        // Después: registrar(Email email, Cedula cedula). Invertirlos no compila.
        String antes = """
                class Antes {
                    static void registrar(String email, String cedula) { }
                    static void usar() { registrar("1020304050", "ana@correo.example"); }
                }
                """;
        String despues = """
                class Despues {
                    record Email(String valor) { }
                    record Cedula(String valor) { }
                    static void registrar(Email email, Cedula cedula) { }
                    static void usar() { registrar(new Cedula("1020304050"), new Email("ana@correo.example")); }
                }
                """;
        comprobar("con String, invertir email y cédula compila (el error llega a producción)", compila("Antes", antes));
        comprobar("con objetos de valor, invertirlos no compila", !compila("Despues", despues));
    }

    // ------------------------------------------------------------------ 3
    static void punto3Pedido() {
        // Código cliente ANTES, con la clase mutable.
        PedidoMutable mutable = new PedidoMutable("P-1");
        mutable.getItems().add("Teclado");            // modifica la lista interna desde fuera
        mutable.setDireccion("Cra 10 # 20-30");
        PedidoMutable alias = mutable;                // otro "dueño" del mismo objeto
        alias.setDireccion("Calle 5 # 1-2");          // y lo cambia por detrás
        String resumenAntes = mutable.getId() + " " + mutable.getItems() + " " + mutable.getDireccion();

        // Código cliente DESPUÉS, con la clase inmutable.
        Pedido pedido = new Pedido("P-1", List.of(), "Cra 10 # 20-30").conItem("Teclado");
        Pedido corregido = pedido.conDireccion("Calle 5 # 1-2");  // un cambio produce un valor nuevo
        String resumenDespues = corregido.id() + " " + corregido.items() + " " + corregido.direccion();

        comprobar("el código cliente llega al mismo resultado", resumenAntes.equals(resumenDespues));
        comprobar("el pedido anterior no cambió", pedido.direccion().equals("Cra 10 # 20-30"));
        comprobar("la lista no se puede modificar desde fuera",
                lanza(UnsupportedOperationException.class, () -> corregido.items().add("Colado")));
        List<String> origen = new ArrayList<>(List.of("Mouse"));
        Pedido conCopia = new Pedido("P-2", origen, "Cra 1");
        origen.add("Colado");
        comprobar("copia defensiva al construir: cambiar la lista original no afecta al pedido", conCopia.items().equals(List.of("Mouse")));
        System.out.println("   " + resumenDespues);
    }

    // ------------------------------------------------------------------ 4
    static void punto4Hilos() throws InterruptedException {
        long incoherentesMutable = carreraConObjetoMutable();
        System.out.println("   objeto mutable compartido: " + incoherentesMutable + " lecturas incoherentes (ancho distinto de 10)");
        comprobar("con el objeto mutable se observa un estado imposible", incoherentesMutable > 0);

        long incoherentesInmutable = carreraConObjetoInmutable();
        System.out.println("   referencia a objetos inmutables: " + incoherentesInmutable + " lecturas incoherentes");
        comprobar("con objetos inmutables nunca se observa un estado a medias", incoherentesInmutable == 0);
    }

    /** Un hilo mueve el rango campo por campo; el otro lo lee. Corre hasta ver el error o 10 segundos. */
    static long carreraConObjetoMutable() throws InterruptedException {
        RangoMutable rango = new RangoMutable(0, 10);
        AtomicBoolean seguir = new AtomicBoolean(true);
        Thread escritor = new Thread(() -> {
            boolean arriba = false;
            while (seguir.get()) {
                if (arriba) {
                    rango.mover(0, 10);
                } else {
                    rango.mover(100, 110);
                }
                arriba = !arriba;
            }
        });
        escritor.start();
        long incoherentes = 0;
        long limite = System.nanoTime() + 10_000_000_000L;
        long lecturas = 0;
        while (System.nanoTime() < limite && (incoherentes == 0 || lecturas < 1_000_000)) {
            int inicio = rango.inicio();
            int fin = rango.fin();
            if (fin - inicio != 10) {
                incoherentes++;
            }
            lecturas++;
        }
        seguir.set(false);
        escritor.join();
        return incoherentes;
    }

    /** Lo mismo, pero el hilo publica rangos nuevos e inmutables en una referencia volatile. Sin synchronized. */
    static long carreraConObjetoInmutable() throws InterruptedException {
        ContenedorDeRango contenedor = new ContenedorDeRango(new Rango(0, 10));
        AtomicBoolean seguir = new AtomicBoolean(true);
        AtomicLong escrituras = new AtomicLong();
        Thread escritor = new Thread(() -> {
            boolean arriba = false;
            while (seguir.get()) {
                contenedor.actual = arriba ? new Rango(0, 10) : new Rango(100, 110);
                arriba = !arriba;
                escrituras.incrementAndGet();
            }
        });
        escritor.start();
        long incoherentes = 0;
        for (int i = 0; i < 5_000_000; i++) {
            Rango leido = contenedor.actual;      // una sola lectura: un valor completo y coherente
            if (leido.fin() - leido.inicio() != 10) {
                incoherentes++;
            }
        }
        seguir.set(false);
        escritor.join();
        comprobar("el escritor inmutable sí estuvo escribiendo durante la prueba", escrituras.get() > 1000);
        return incoherentes;
    }

    // ------------------------------------------------------------------ 5
    static void punto5ClaveMutable() {
        Map<ClaveMutable, String> mapa = new HashMap<>();
        ClaveMutable clave = new ClaveMutable("CC-1");
        mapa.put(clave, "Ana");
        clave.setCodigo("CC-2");                      // cambia el hashCode de una clave que ya está dentro

        comprobar("get con la misma instancia ya no la encuentra", mapa.get(clave) == null);
        comprobar("get con el valor original tampoco", mapa.get(new ClaveMutable("CC-1")) == null);
        comprobar("pero sigue ocupando espacio en el mapa", mapa.size() == 1 && mapa.containsValue("Ana"));
        System.out.println("   tamaño " + mapa.size() + ", get(clave) = " + mapa.get(clave) + ": la entrada quedó perdida");

        Map<Cedula, String> conValor = new HashMap<>();
        conValor.put(new Cedula("1020304050"), "Ana");
        comprobar("con un objeto de valor inmutable como clave sí se encuentra", "Ana".equals(conValor.get(new Cedula("1.020.304.050"))));
    }

    // ------------------------------------------------------------------ utilidades
    static void comprobar(String descripcion, boolean ok) {
        System.out.println((ok ? "   PASA  " : "   FALLA ") + descripcion);
        if (!ok) {
            fallos++;
        }
    }

    static boolean lanza(Class<? extends Throwable> tipo, Runnable accion) {
        try {
            accion.run();
            return false;
        } catch (Throwable e) {
            return tipo.isInstance(e);
        }
    }

    static boolean compila(String clase, String codigo) throws Exception {
        Path carpeta = Files.createTempDirectory("taller06");
        Path fuente = carpeta.resolve(clase + ".java");
        Files.writeString(fuente, codigo);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        int codigoSalida = javac.run(null, null, new ByteArrayOutputStream(), "-d", carpeta.toString(), fuente.toString());
        return codigoSalida == 0;
    }
}

// ====================================================================== objetos de valor

record Periodo(LocalDate inicio, LocalDate fin) {

    Periodo {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fin, "fin");
        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException("El fin " + fin + " es anterior al inicio " + inicio);
        }
    }

    boolean contiene(LocalDate fecha) {
        return !fecha.isBefore(inicio) && !fecha.isAfter(fin);
    }

    boolean seSolapaCon(Periodo otro) {
        return !otro.fin.isBefore(inicio) && !otro.inicio.isAfter(fin);
    }

    Periodo extendidoHasta(LocalDate nuevoFin) {
        if (nuevoFin.isBefore(fin)) {
            throw new IllegalArgumentException("Extender hasta " + nuevoFin + " acortaría el periodo que termina el " + fin);
        }
        return new Periodo(inicio, nuevoFin);
    }
}

record Email(String valor) {

    private static final Pattern FORMATO = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    Email {
        Objects.requireNonNull(valor, "email");
        valor = valor.strip().toLowerCase();
        if (!FORMATO.matcher(valor).matches()) {
            throw new IllegalArgumentException("Correo inválido: " + valor);
        }
    }
}

record Cedula(String valor) {

    Cedula {
        Objects.requireNonNull(valor, "cédula");
        valor = valor.replace(".", "").strip();
        if (!valor.matches("\\d{6,10}")) {
            throw new IllegalArgumentException("Cédula inválida: " + valor);
        }
    }
}

// ====================================================================== punto 3

/** La clase mutable de partida: setters, y un getter que entrega la lista interna. */
class PedidoMutable {
    private final String id;
    private final List<String> items = new ArrayList<>();
    private String direccion;

    PedidoMutable(String id) { this.id = id; }

    String getId() { return id; }
    List<String> getItems() { return items; }
    String getDireccion() { return direccion; }
    void setDireccion(String direccion) { this.direccion = direccion; }
}

/** La misma clase, inmutable: copia defensiva al entrar, lista inmodificable al salir, "con..." en vez de "set...". */
record Pedido(String id, List<String> items, String direccion) {

    Pedido {
        Objects.requireNonNull(id, "id");
        items = List.copyOf(items);
    }

    Pedido conItem(String item) {
        List<String> nuevos = new ArrayList<>(items);
        nuevos.add(item);
        return new Pedido(id, nuevos, direccion);
    }

    Pedido conDireccion(String nuevaDireccion) {
        return new Pedido(id, items, nuevaDireccion);
    }
}

// ====================================================================== punto 4

class RangoMutable {
    // volatile: así cada lectura ve el último valor escrito. El problema que queda no es de visibilidad
    // sino de atomicidad: el par (inicio, fin) se escribe en dos pasos y otro hilo puede leer entre ambos.
    private volatile int inicio;
    private volatile int fin;

    RangoMutable(int inicio, int fin) { mover(inicio, fin); }

    void mover(int nuevoInicio, int nuevoFin) {
        inicio = nuevoInicio;
        fin = nuevoFin;
    }

    int inicio() { return inicio; }
    int fin() { return fin; }
}

record Rango(int inicio, int fin) {
}

class ContenedorDeRango {
    volatile Rango actual;

    ContenedorDeRango(Rango inicial) { actual = inicial; }
}

// ====================================================================== punto 5

class ClaveMutable {
    private String codigo;

    ClaveMutable(String codigo) { this.codigo = codigo; }

    void setCodigo(String codigo) { this.codigo = codigo; }

    @Override
    public boolean equals(Object o) {
        return o instanceof ClaveMutable otra && otra.codigo.equals(codigo);
    }

    @Override
    public int hashCode() {
        return codigo.hashCode();
    }
}
