package tienda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import comun.Ev;
import comun.Kf;
import inventario.adaptadores.entrada.http.ReservaHttp;
import inventario.adaptadores.entrada.kafka.ConsumidorDePedidos;
import inventario.adaptadores.entrada.kafka.ConsumidorMain;
import inventario.adaptadores.salida.jdbc.InventarioJdbc;
import inventario.aplicacion.ReservarPorPedido.Orden;
import inventario.aplicacion.ReservarPorPedido.Resultado;
import inventario.aplicacion.ServicioDeInventario;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tienda.adaptadores.entrada.http.ServidorHttp;
import tienda.adaptadores.salida.kafka.MensajeroKafka;
import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;
import tienda.adaptadores.salida.memoria.CatalogoEnMemoria;
import tienda.adaptadores.salida.sistema.RelojDelSistema;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.puertos.entrada.CrearPedido;
import tienda.dominio.Dinero;
import tienda.dominio.Existencias;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TiendaTest {

    static final String B = Kf.bootstrap();
    static final String CONTENEDOR = "pub-kafka-y-sagas-kafka-1";
    static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeEach
    void limpio() throws Exception {
        arrancarKafka();
        inventario.adaptadores.salida.jdbc.UnidadJdbc.esquemaNuevo(100);
        Kf.tema(B, MensajeroKafka.TEMA, 3, 1, Map.of());
        Kf.tema(B, ConsumidorDePedidos.FALLIDOS, 1, 1, Map.of());
    }

    @AfterEach
    void kafkaArriba() {
        arrancarKafka();
    }

    static void arrancarKafka() {
        Kf.docker("start", CONTENEDOR);
        Kf.esperar(() -> { try (var a = Kf.admin(B)) { a.listTopics().names().get(5, TimeUnit.SECONDS); return true; } }, 90_000, "Kafka arriba");
    }

    static long contar(String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(Kf.pgUrl(), "taller", "taller"); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static String evento(String id, String pedido, int cantidad, String extra) {
        return "{\"id\":\"" + id + "\",\"tipo\":\"PedidoCreado\",\"version\":1,\"datos\":{\"pedidoId\":\"" + pedido + "\",\"producto\":\"libro\",\"cantidad\":" + cantidad + extra + "}}";
    }

    static void publicar(String clave, String valor) throws Exception {
        try (var p = new KafkaProducer<String, String>(Map.of("bootstrap.servers", B, "key.serializer", StringSerializer.class.getName(), "value.serializer", StringSerializer.class.getName(), "acks", "all"))) {
            p.send(new ProducerRecord<>(MensajeroKafka.TEMA, clave, valor)).get();
        }
    }

    static ServicioDeInventario inventario() {
        var unidad = new inventario.adaptadores.salida.jdbc.UnidadJdbc();
        var jdbc = new InventarioJdbc(unidad);
        return new ServicioDeInventario(jdbc, jdbc, unidad);
    }

    /** El servicio de pedidos del módulo hexagonal tal cual: sus adaptadores en memoria, su catálogo y su bandeja de salida. */
    record Pedidos(AlmacenEnMemoria almacen, ServicioDePedidos servicio) {
    }

    static Pedidos pedidos() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        almacen.guardar(Existencias.de("libro", 1000));
        var catalogo = new CatalogoEnMemoria(Map.of("libro", new Dinero(1_000_000)));
        return new Pedidos(almacen, new ServicioDePedidos(almacen, catalogo, almacen, almacen, almacen, new RelojDelSistema(), almacen));
    }

    static CrearPedido.Resultado crear(ServicioDePedidos s, String clave, int cantidad) {
        return s.crear(new CrearPedido.Solicitud(clave, "cliente-1", List.of(new CrearPedido.Linea("libro", cantidad))));
    }

    static int post(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    // ------------------------------------------------------------------ paso 2 y criterios de arquitectura
    static final Path ORIGEN = Path.of("../../../../../hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src");

    @Test
    @Order(1)
    void paso2_elDominioYLaAplicacionDePedidosNoCambiaron() throws Exception {
        // Lo que se compiló es una copia de solo lectura del servicio hexagonal (target/hexagonal-base). Se compara byte a byte con el origen.
        int archivos = 0;
        for (String paquete : List.of("tienda/dominio", "tienda/aplicacion")) {
            try (Stream<Path> todos = Files.walk(ORIGEN.resolve(paquete))) {
                for (Path o : todos.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    Path copia = Path.of("target/hexagonal-base").resolve(ORIGEN.relativize(o));
                    assertTrue(Arrays.equals(Files.readAllBytes(o), Files.readAllBytes(copia)), o + " difiere de lo compilado");
                    archivos++;
                }
            }
            // y el módulo no añade ni modifica nada en esos paquetes
            assertTrue(!Files.exists(Path.of("src/main/java").resolve(paquete)), "src/main/java/" + paquete + " no debe existir: esos paquetes son del servicio de pedidos");
        }
        // Lo único que el taller añadió al servicio de pedidos: un archivo, un adaptador de salida
        try (Stream<Path> propios = Files.walk(Path.of("src/main/java/tienda"))) {
            List<String> nuevos = propios.filter(Files::isRegularFile).map(p -> Path.of("src/main/java").relativize(p).toString()).collect(Collectors.toList());
            Ev.nota("2. dominio y aplicación de Pedidos: " + archivos + " archivos del servicio hexagonal, iguales byte a byte al origen (" + ORIGEN + "); archivos nuevos en el paquete tienda: " + nuevos);
            assertEquals(List.of("tienda/adaptadores/salida/kafka/MensajeroKafka.java"), nuevos);
        }
    }

    @Test
    @Order(2)
    void criterios_kafkaSoloEnAdaptadoresYLosServiciosNoComparten() throws Exception {
        List<Path> java = new java.util.ArrayList<>();
        for (String raiz : List.of("src/main/java", "target/hexagonal-base")) {
            try (Stream<Path> todos = Files.walk(Path.of(raiz))) {
                todos.filter(p -> p.toString().endsWith(".java") && !p.startsWith("src/main/java/comun")).forEach(java::add);   // comun/ son las utilidades de los talleres, no un servicio
            }
        }
        int fueraDeAdaptadores = 0, conKafka = 0;
        for (Path p : java) {
            String texto = Files.readString(p);
            boolean adaptador = p.toString().contains("/adaptadores/");
            boolean main = p.getFileName().toString().equals("Main.java");
            if (!adaptador && !main) {
                fueraDeAdaptadores++;
                assertTrue(!texto.contains("import org.apache.kafka") && !texto.contains("import org.springframework.kafka") && !texto.contains("import java.sql") && !texto.contains("import comun."), p + " importa infraestructura");
            } else if (texto.contains("import org.apache.kafka")) conKafka++;
            boolean esInventario = p.toString().contains("/inventario/");
            if (esInventario) assertTrue(!texto.contains("import tienda."), p + " importa pedidos");
            else assertTrue(!texto.contains("import inventario."), p + " importa inventario");
        }
        Ev.nota("criterios: " + fueraDeAdaptadores + " archivos de dominio y aplicación (de los dos servicios) sin import de Kafka, SQL ni comun; Kafka solo en " + conKafka + " archivos de adaptadores; Pedidos (tienda.*) e Inventario no se importan entre sí");
    }

    // ------------------------------------------------------------------ paso 5
    @Test
    @Order(3)
    void paso5_elCasoDeUsoDeInventarioSinKafka() throws Exception {
        // Se llama al puerto de entrada desde una prueba; ni un broker de por medio.
        var servicio = inventario();
        assertEquals(Resultado.RESERVADO, servicio.reservar(new Orden("e1", "p1", "libro", 40)));
        assertEquals(Resultado.SIN_EXISTENCIAS, servicio.reservar(new Orden("e2", "p2", "libro", 61)));
        assertEquals(Resultado.RESERVADO, servicio.reservar(new Orden("e3", "p3", "libro", 60)));
        assertEquals(Resultado.SIN_EXISTENCIAS, servicio.reservar(new Orden("e4", "p4", "libro", 1)));
        assertEquals(0, contar("SELECT disponibles FROM k08i.stock"));
        Ev.nota("5. puerto de entrada llamado directamente: reservas 40 + 60 aceptadas, 61 y 1 rechazadas por falta de existencias; stock final 0; sin broker");
    }

    // ------------------------------------------------------------------ pasos 3 y 4, 6
    @Test
    @Order(4)
    void paso6_elMismoEventoTresVecesReservaUnaVez() throws Exception {
        var servicio = inventario();
        try (var consumidor = new ConsumidorDePedidos(B, Kf.unico("inv"), servicio)) {
            consumidor.arrancar();
            for (int i = 0; i < 3; i++) publicar("p-1", evento("e-unico", "p-1", 10, ""));
            publicar("p-2", evento("e-otro", "p-2", 5, ""));
            Kf.esperar(() -> contar("SELECT count(*) FROM k08i.reservas") == 2, 60_000, "dos reservas");
            Thread.sleep(1500);
        }
        Ev.nota("6. el mismo evento publicado 3 veces (+1 distinto): reservas " + contar("SELECT count(*) FROM k08i.reservas") + ", stock " + contar("SELECT disponibles FROM k08i.stock") + " (100 - 10 - 5)");
        assertEquals(2, contar("SELECT count(*) FROM k08i.reservas"));
        assertEquals(85, contar("SELECT disponibles FROM k08i.stock"));
    }

    @Test
    @Order(5)
    void paso4_mensajeInvalidoVaAFallidosYElSiguienteSeProcesa() throws Exception {
        var servicio = inventario();
        try (var consumidor = new ConsumidorDePedidos(B, Kf.unico("inv"), servicio)) {
            consumidor.arrancar();
            publicar("x", "esto no es json");
            publicar("y", "{\"id\":\"sin-datos\"}");
            publicar("p-1", evento("e-1", "p-1", 7, ""));
            Kf.esperar(() -> contar("SELECT count(*) FROM k08i.reservas") == 1 && Kf.contar(B, ConsumidorDePedidos.FALLIDOS) == 2, 60_000, "fallidos y siguiente");
        }
        var f = Kf.leerTodo(ConsumidorDePedidos.FALLIDOS);
        Ev.nota("4. dos mensajes inválidos -> fallidos con motivo y origen: " + f.stream().map(r -> new String(r.headers().lastHeader("motivo").value()) + " / " + new String(r.headers().lastHeader("origen").value())).toList());
        assertEquals(2, f.size());
    }

    // ------------------------------------------------------------------ paso 7
    @Test
    @Order(6)
    void paso7_muerteTrasProcesarYAntesDeConfirmarNoReservaDosVeces() throws Exception {
        for (int i = 1; i <= 10; i++) publicar("p-" + i, evento("e-" + i, "p-" + i, 2, ""));
        Process muere = Kf.hijo(ConsumidorMain.class, "3");
        Kf.recoger(muere);
        assertTrue(muere.waitFor(90, TimeUnit.SECONDS), "debía morir");
        assertEquals(137, muere.exitValue());
        long tras = contar("SELECT count(*) FROM k08i.reservas");
        Ev.nota("7. consumidor muerto tras procesar el tercer mensaje y antes de confirmar su posición: reservas hechas " + tras);
        Process vivo = Kf.hijo(ConsumidorMain.class, "0");
        Kf.recoger(vivo);
        try {
            Kf.esperar(() -> contar("SELECT count(*) FROM k08i.vistos") == 10, 90_000, "los diez eventos vistos");
            Thread.sleep(2000);
        } finally {
            vivo.destroy();
            vivo.waitFor(20, TimeUnit.SECONDS);
        }
        Ev.nota("7. tras reiniciar: reservas " + contar("SELECT count(*) FROM k08i.reservas") + " (10 pedidos), reservas distintas " + contar("SELECT count(DISTINCT pedido_id) FROM k08i.reservas") + ", stock " + contar("SELECT disponibles FROM k08i.stock") + " (100 - 10 x 2)");
        assertEquals(10, contar("SELECT count(*) FROM k08i.reservas"));
        assertEquals(10, contar("SELECT count(DISTINCT pedido_id) FROM k08i.reservas"));
        assertEquals(80, contar("SELECT disponibles FROM k08i.stock"));
    }

    // ------------------------------------------------------------------ paso 8
    @Test
    @Order(7)
    void paso8_conKafkaCaidoLaApiSigueYLosEventosEsperan() throws Exception {
        Kf.docker("stop", CONTENEDOR);
        int[] codigos = new int[3];
        var p = pedidos();
        var http = new ServidorHttp(0, p.servicio(), p.servicio(), p.servicio(), p.servicio(), p.servicio(), p.servicio());
        http.iniciar();
        try (var mensajero = new MensajeroKafka(B, p.servicio(), false)) {
            for (int i = 1; i <= 3; i++) {
                codigos[i - 1] = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + http.puerto() + "/pedidos")).header("Idempotency-Key", "k-" + i)
                        .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("cliente=c-" + i + "&linea=libro:" + i)).build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode();
            }
            assertEquals(0, mensajero.unaVuelta(p.almacen()::entregarPendientes), "el mensajero no puede publicar");
            Ev.nota("8. Kafka detenido: la API respondió " + java.util.Arrays.toString(codigos) + "; pedidos en el servicio " + p.almacen().deCliente("c-1").size() + "+" + p.almacen().deCliente("c-2").size() + "+" + p.almacen().deCliente("c-3").size()
                    + "; el mensajero no pudo entregar ninguno (siguen en la bandeja)");
            assertEquals(List.of(201, 201, 201), java.util.Arrays.stream(codigos).boxed().toList());
            arrancarKafka();
            assertEquals(3, mensajero.unaVuelta(p.almacen()::entregarPendientes));
            assertEquals(0, mensajero.unaVuelta(p.almacen()::entregarPendientes), "la bandeja quedó vacía");
        } finally {
            http.detener();
        }
        List<ConsumerRecord<String, String>> enElTema = Kf.leerTodo(MensajeroKafka.TEMA);
        Map<Integer, List<String>> porParticion = new HashMap<>();
        enElTema.forEach(r -> porParticion.computeIfAbsent(r.partition(), k -> new java.util.ArrayList<>()).add(r.key()));
        Ev.nota("8. Kafka de vuelta: " + enElTema.size() + " eventos en el tema; claves por partición, en el orden de las posiciones: " + porParticion);
        assertEquals(3, enElTema.size());
        for (var r : enElTema) assertEquals(r.key(), r.value().replaceAll(".*\"pedidoId\":\"([^\"]+)\".*", "$1"), "la clave es el id del pedido");
        for (List<String> claves : porParticion.values()) {            // dentro de una partición, el orden es el de la bandeja
            assertEquals(claves.stream().sorted().toList(), claves);
        }
    }

    // ------------------------------------------------------------------ paso 9
    @Test
    @Order(8)
    void paso9_campoNuevoSinTocarInventario() throws Exception {
        var p = pedidos();
        String idPedido = crear(p.servicio(), "k-nuevo", 4).pedido().id();                 // ahora el evento sale con "canal" y versión 2
        try (var mensajero = new MensajeroKafka(B, p.servicio(), true); var consumidor = new ConsumidorDePedidos(B, Kf.unico("inv"), inventario())) {
            assertEquals(1, mensajero.unaVuelta(p.almacen()::entregarPendientes));
            consumidor.arrancar();
            Kf.esperar(() -> contar("SELECT count(*) FROM k08i.reservas") == 1, 60_000, "reserva");
        }
        String valor = Kf.leerTodo(MensajeroKafka.TEMA).get(0).value();
        Ev.nota("9. evento con campo nuevo: " + valor + " -> Inventario (sin cambios) reservó " + contar("SELECT cantidad FROM k08i.reservas") + " unidades");
        assertTrue(valor.contains("\"canal\":\"web\""));
        assertEquals(4, contar("SELECT cantidad FROM k08i.reservas"));
    }

    // ------------------------------------------------------------------ paso 10
    @Test
    @Order(9)
    void paso10_segundoAdaptadorDeEntradaConHttp() throws Exception {
        try (var http = new ReservaHttp(inventario())) {
            var r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + http.puerto() + "/reservas?evento=h1&pedido=p-h&producto=libro&cantidad=3")).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            var otra = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + http.puerto() + "/reservas?evento=h1&pedido=p-h&producto=libro&cantidad=3")).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals("RESERVADO", r.body());
            assertEquals("YA_PROCESADO", otra.body());
        }
        long lineasAdaptador = Files.readAllLines(Path.of("src/main/java/inventario/adaptadores/entrada/http/ReservaHttp.java")).stream().filter(l -> !l.isBlank() && !l.trim().startsWith("import") && !l.trim().startsWith("/") && !l.trim().startsWith("*")).count();
        long lineasNegocio = Stream.of("src/main/java/inventario/dominio/Existencias.java", "src/main/java/inventario/aplicacion/ServicioDeInventario.java")
                .mapToLong(f -> { try { return Files.readAllLines(Path.of(f)).size(); } catch (java.io.IOException e) { throw new IllegalStateException(e); } }).sum();
        Ev.nota("10. el adaptador HTTP tiene " + lineasAdaptador + " líneas de código (solo traducción) y repite 0 líneas de negocio (la regla y la idempotencia, " + lineasNegocio + " líneas, se reutilizan); el reintento por HTTP devuelve YA_PROCESADO");
    }
}
