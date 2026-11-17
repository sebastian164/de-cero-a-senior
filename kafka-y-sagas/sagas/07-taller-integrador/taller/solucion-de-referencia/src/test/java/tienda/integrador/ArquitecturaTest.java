package tienda.integrador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import comun.Ev;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Los requisitos de diseño del taller, comprobados leyendo los import: cada servicio es un hexágono y no comparten clases. */
class ArquitecturaTest {

    static final Path RAIZ = Path.of("src/main/java/tienda/integrador");
    static final List<String> SERVICIOS = List.of("pedidos", "orquestador", "inventario", "pagos", "envios", "notificaciones", "fidelidad");
    static final List<String> PROHIBIDOS_EN_EL_NUCLEO = List.of("org.springframework", "org.apache.kafka", "java.sql", "javax.sql", "com.fasterxml", "com.sun.net", "java.net", "org.postgresql");

    static List<Path> java(Path donde) throws IOException {
        try (Stream<Path> t = Files.walk(donde)) {
            return t.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }

    static List<String> imports(Path p) throws IOException {
        try (Stream<String> l = Files.lines(p)) {
            return l.filter(x -> x.startsWith("import ")).map(x -> x.replace("import ", "").replace("static ", "").replace(";", "").trim()).collect(Collectors.toList());
        }
    }

    @Test
    void dominioYAplicacionNoImportanTecnologia() throws IOException {
        int archivos = 0;
        for (String s : SERVICIOS) {
            for (String capa : List.of("dominio", "aplicacion")) {
                Path dir = RAIZ.resolve(s).resolve(capa);
                if (!Files.exists(dir)) continue;
                for (Path p : java(dir)) {
                    archivos++;
                    for (String i : imports(p)) {
                        for (String prohibido : PROHIBIDOS_EN_EL_NUCLEO) assertTrue(!i.startsWith(prohibido), p + " importa " + i);
                        boolean ok = i.startsWith("java.util.") || i.startsWith("java.time.") || i.startsWith("org.slf4j.") || i.startsWith("tienda.integrador." + s + ".");
                        assertTrue(ok, p + " importa " + i + ": el núcleo solo ve java.util, java.time, el registro y su propio servicio");
                    }
                }
            }
        }
        Ev.nota("A1. " + archivos + " archivos de dominio y aplicación de los siete servicios: sin Spring, Kafka, SQL, HTTP ni JSON; solo java.util, java.time, slf4j y su propio paquete");
        assertTrue(archivos >= 20);
    }

    @Test
    void kafkaYJdbcSoloEnAdaptadoresYLosServiciosNoSeImportanEntreSi() throws IOException {
        int adaptadores = 0;
        for (String s : SERVICIOS) {
            for (Path p : java(RAIZ.resolve(s))) {
                boolean adaptador = p.toString().contains("/adaptadores/");
                boolean composicion = p.toString().contains("/configuracion/");
                for (String i : imports(p)) {
                    for (String otro : SERVICIOS) {
                        if (!otro.equals(s)) assertTrue(!i.startsWith("tienda.integrador." + otro + "."), p + " importa a otro servicio: " + i);
                    }
                    if (!adaptador && !composicion) {
                        assertTrue(!i.startsWith("org.apache.kafka") && !i.startsWith("org.springframework") && !i.startsWith("java.sql"), p + " fuera de los adaptadores importa " + i);
                    }
                }
                if (adaptador) adaptadores++;
            }
        }
        Ev.nota("A2. " + adaptadores + " archivos de adaptadores; ningún servicio importa a otro (solo comparten la biblioteca de plataforma: tabla de salida, errores y lectura de JSON)");
    }

    @Test
    void losListenersSoloTraducenYLlamanAUnPuertoDeEntrada() throws IOException {
        int listeners = 0;
        for (String s : SERVICIOS) {
            for (Path p : java(RAIZ.resolve(s))) {
                String texto = Files.readString(p);
                if (!texto.contains("@KafkaListener")) continue;
                listeners++;
                assertTrue(!texto.contains("JdbcTemplate") && !texto.contains("jdbc."), p + ": un @KafkaListener no toca la base de datos");
                assertTrue(imports(p).stream().anyMatch(i -> i.startsWith("tienda.integrador." + s + ".aplicacion.Puertos.")), p + " debe llamar a un puerto de entrada");
            }
        }
        assertEquals(8, listeners);
        Ev.nota("A3. " + listeners + " @KafkaListener, todos sin acceso a la base y todos llamando a un puerto de entrada de su servicio");
    }

    @Test
    void elOrquestadorUsaLasMismasTransicionesQueLaSesion02() throws IOException {
        for (String archivo : List.of("Transiciones.java", "Estado.java")) {
            String aqui = Files.readString(RAIZ.resolve("orquestador/dominio").resolve(archivo)).replaceFirst("package [^;]+;", "");
            String sesion05 = Files.readString(Path.of("../../../05-sagas-con-kafka-y-spring-boot/taller/solucion-de-referencia/src/main/java/tienda").resolve(archivo)).replaceFirst("package [^;]+;", "");
            String sesion02 = Files.readString(Path.of("../../../02-saga-orquestada/taller/solucion-de-referencia/src/main/java/tienda").resolve(archivo)).replaceFirst("package [^;]+;", "");
            assertEquals(sesion02, aqui, archivo + " no es el de la sesión 02 (salvo la línea package)");
            assertEquals(sesion05, aqui);
        }
        Ev.nota("A4. Transiciones.java y Estado.java del orquestador: idénticos a los de las sesiones 02 y 05, salvo la línea package");
    }

    @Test
    void elOrquestadorEsUnServicioDeAplicacionConCuatroPuertos() throws IOException {
        List<String> puertos = new ArrayList<>();
        for (String linea : Files.readAllLines(RAIZ.resolve("orquestador/aplicacion/Puertos.java"))) {
            if (linea.trim().startsWith("public interface ")) puertos.add(linea.trim().split("\\s+")[2]);
        }
        for (String necesario : List.of("RepositorioDeSagas", "MensajesProcesados", "BandejaDeSalida", "UnidadDeTrabajo")) assertTrue(puertos.contains(necesario), necesario);
        Ev.nota("A5. puertos del orquestador: " + puertos);
    }
}
