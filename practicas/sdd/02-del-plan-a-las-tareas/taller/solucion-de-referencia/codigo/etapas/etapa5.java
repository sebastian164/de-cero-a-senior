import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bitácora: la aplicación de práctica de los talleres de Docker y Kubernetes.
 * Un solo archivo y solo el JDK, para que la atención esté en el contenedor y no en la aplicación.
 * Toda la configuración llega por variables de entorno.
 */
public class Servidor {

    private static final AtomicBoolean listo = new AtomicBoolean(false);
    private static final AtomicLong visitas = new AtomicLong();

    /** Criterio 6: máximo de etiquetas distintas por nota. */
    static final int MAXIMO_ETIQUETAS = 10;
    private static final int LARGO_MAXIMO_ETIQUETA = 30;
    // Un # al comienzo de una palabra (principio del texto o tras un espacio) y la serie de caracteres permitidos que lo sigue.
    private static final Pattern ETIQUETA = Pattern.compile("(?<!\\S)#([\\p{L}\\p{N}_-]+)");
    private static final Pattern ETIQUETA_VALIDA = Pattern.compile("[\\p{L}\\p{N}_-]{1,30}");

    public static void main(String[] args) throws Exception {
        int puerto = Integer.parseInt(entorno("PUERTO", "8080"));
        String mensaje = entorno("MENSAJE", "Hola desde la bitácora");
        String version = entorno("VERSION", "dev");
        String clave = entorno("CLAVE_API", "");
        long retardoArranque = Long.parseLong(entorno("RETARDO_ARRANQUE_MS", "0"));
        long esperaApagado = Long.parseLong(entorno("ESPERA_APAGADO_MS", "0"));
        Path directorio = Path.of(entorno("DIRECTORIO_DATOS", "datos"));
        Path archivoNotas = directorio.resolve("notas.txt");
        String host = nombreDelHost();

        HttpServer servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        servidor.setExecutor(Executors.newFixedThreadPool(8));

        // ¿El proceso está vivo? No comprueba dependencias: solo que el servidor responde.
        servidor.createContext("/salud/vivo", registrar(peticion -> responder(peticion, 200, "text/plain", "vivo")));

        // ¿Puede atender tráfico ahora? Falso mientras arranca, si no puede escribir datos o si se está apagando.
        servidor.createContext("/salud/listo", registrar(peticion -> {
            if (listo.get()) {
                responder(peticion, 200, "text/plain", "listo");
            } else {
                responder(peticion, 503, "text/plain", "no listo");
            }
        }));

        servidor.createContext("/api/info", registrar(peticion -> {
            if (rechazarSiNoEstaListo(peticion)) {
                return;
            }
            String cuerpo = "{"
                    + "\"mensaje\":" + json(mensaje) + ","
                    + "\"version\":" + json(version) + ","
                    + "\"host\":" + json(host) + ","
                    + "\"visitas\":" + visitas.incrementAndGet() + ","
                    + "\"notas\":" + leerNotas(archivoNotas).size()
                    + "}";
            responder(peticion, 200, "application/json", cuerpo);
        }));

        servidor.createContext("/api/notas", registrar(peticion -> {
            if (rechazarSiNoEstaListo(peticion)) {
                return;
            }
            switch (peticion.getRequestMethod()) {
                case "GET" -> {
                    List<String> notas = leerNotas(archivoNotas);
                    String filtro = parametroTexto(peticion, "etiqueta");
                    if (filtro != null) {
                        String buscada = filtro.startsWith("#") ? filtro.substring(1) : filtro;
                        if (!ETIQUETA_VALIDA.matcher(buscada).matches()) {
                            responder(peticion, 400, "application/json", "{\"error\":\"la etiqueta debe tener de 1 a 30 letras, dígitos, _ o -\"}");
                            return;
                        }
                        buscada = buscada.toLowerCase(Locale.ROOT);
                        String etiqueta = buscada;
                        notas = notas.stream().filter(nota -> etiquetas(nota).contains(etiqueta)).toList();
                    }
                    String elementos = notas.stream().map(Servidor::json).collect(Collectors.joining(","));
                    responder(peticion, 200, "application/json", "{\"host\":" + json(host) + ",\"notas\":[" + elementos + "]}");
                }
                case "POST" -> {
                    if (!clave.isEmpty() && !clave.equals(peticion.getRequestHeaders().getFirst("X-Clave"))) {
                        responder(peticion, 401, "application/json", "{\"error\":\"clave ausente o incorrecta\"}");
                        return;
                    }
                    String nota = new String(peticion.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                            .replaceAll("\\s+", " ").trim();
                    if (nota.isEmpty() || nota.length() > 500) {
                        responder(peticion, 400, "application/json", "{\"error\":\"la nota debe tener entre 1 y 500 caracteres\"}");
                        return;
                    }
                    if (etiquetas(nota).size() > MAXIMO_ETIQUETAS) {
                        responder(peticion, 400, "application/json", "{\"error\":\"una nota admite como máximo " + MAXIMO_ETIQUETAS + " etiquetas distintas\"}");
                        return;
                    }
                    synchronized (Servidor.class) {
                        Files.writeString(archivoNotas, nota + System.lineSeparator(), StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    }
                    String etiquetasJson = etiquetas(nota).stream().map(Servidor::json).collect(Collectors.joining(","));
                    responder(peticion, 201, "application/json",
                            "{\"guardada\":" + json(nota) + ",\"host\":" + json(host) + ",\"etiquetas\":[" + etiquetasJson + "]}");
                }
                default -> responder(peticion, 405, "application/json", "{\"error\":\"método no permitido\"}");
            }
        }));

        // Gasta CPU a propósito durante los milisegundos pedidos (máximo 5000). Sirve para probar el escalado.
        servidor.createContext("/api/carga", registrar(peticion -> {
            long milisegundos = Math.min(5000, Math.max(0, parametro(peticion, "ms", 200)));
            long fin = System.nanoTime() + milisegundos * 1_000_000;
            double acumulado = 0;
            while (System.nanoTime() < fin) {
                acumulado += Math.sqrt(Math.random());
            }
            responder(peticion, 200, "application/json",
                    "{\"host\":" + json(host) + ",\"ms\":" + milisegundos + ",\"resultado\":" + Math.round(acumulado) + "}");
        }));

        servidor.createContext("/", registrar(peticion -> responder(peticion, 404, "application/json",
                "{\"error\":\"ruta desconocida\",\"rutas\":[\"/salud/vivo\",\"/salud/listo\",\"/api/info\",\"/api/notas\",\"/api/carga\"]}")));

        // Apagado ordenado: al recibir SIGTERM se deja de estar listo, se espera y se terminan las peticiones en curso.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            registro("apagado solicitado: dejo de estar listo");
            listo.set(false);
            dormir(esperaApagado);
            servidor.stop(2);
            registro("apagado completo");
        }));

        servidor.start();
        registro("escuchando en el puerto " + puerto + " (version " + version + ", host " + host + ")");

        if (retardoArranque > 0) {
            registro("arranque lento simulado: " + retardoArranque + " ms");
            dormir(retardoArranque);
        }
        try {
            Files.createDirectories(directorio);
            Path prueba = Files.createTempFile(directorio, "prueba", ".tmp");
            Files.delete(prueba);
            listo.set(true);
            registro("listo para atender; datos en " + directorio.toAbsolutePath());
        } catch (IOException error) {
            registro("ERROR no puedo escribir en " + directorio.toAbsolutePath() + ": " + error + ". Sigo vivo, pero no listo.");
        }
    }

    private interface Manejador {
        void atender(HttpExchange peticion) throws IOException;
    }

    /** Envuelve un manejador para escribir una línea por petición en la salida estándar. */
    private static HttpHandler registrar(Manejador manejador) {
        return peticion -> {
            long inicio = System.nanoTime();
            try {
                manejador.atender(peticion);
            } catch (IOException | RuntimeException error) {
                registro("ERROR " + peticion.getRequestURI() + ": " + error);
                if (peticion.getResponseCode() == -1) {
                    responder(peticion, 500, "application/json", "{\"error\":\"fallo interno\"}");
                }
            } finally {
                long milisegundos = (System.nanoTime() - inicio) / 1_000_000;
                registro(peticion.getRequestMethod() + " " + peticion.getRequestURI() + " " + peticion.getResponseCode() + " " + milisegundos + "ms");
                peticion.close();
            }
        };
    }

    private static boolean rechazarSiNoEstaListo(HttpExchange peticion) throws IOException {
        if (listo.get()) {
            return false;
        }
        responder(peticion, 503, "application/json", "{\"error\":\"el servicio no está listo\"}");
        return true;
    }

    private static void responder(HttpExchange peticion, int estado, String tipo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        peticion.getResponseHeaders().set("Content-Type", tipo + "; charset=utf-8");
        peticion.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = peticion.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private static List<String> leerNotas(Path archivo) throws IOException {
        if (!Files.exists(archivo)) {
            return List.of();
        }
        return Files.readAllLines(archivo, StandardCharsets.UTF_8).stream().filter(linea -> !linea.isBlank()).toList();
    }

    /** Las etiquetas de un texto: sin #, en minúsculas, sin repetir, en orden de aparición. Criterios 1 a 3. */
    static List<String> etiquetas(String texto) {
        LinkedHashSet<String> encontradas = new LinkedHashSet<>();
        Matcher coincidencia = ETIQUETA.matcher(texto);
        while (coincidencia.find()) {
            String serie = coincidencia.group(1);
            if (serie.length() <= LARGO_MAXIMO_ETIQUETA) {
                encontradas.add(serie.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(encontradas);
    }

    /** El valor decodificado de un parámetro de la consulta, o null si no viene. Distingue "vacío" de "ausente". */
    private static String parametroTexto(HttpExchange peticion, String nombre) {
        String consulta = peticion.getRequestURI().getRawQuery();
        if (consulta == null) {
            return null;
        }
        for (String par : consulta.split("&")) {
            String[] partes = par.split("=", 2);
            if (partes[0].equals(nombre)) {
                return partes.length == 2 ? java.net.URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "";
            }
        }
        return null;
    }

    private static long parametro(HttpExchange peticion, String nombre, long porDefecto) {
        String consulta = peticion.getRequestURI().getQuery();
        if (consulta == null) {
            return porDefecto;
        }
        for (String par : consulta.split("&")) {
            String[] partes = par.split("=", 2);
            if (partes.length == 2 && partes[0].equals(nombre)) {
                try {
                    return Long.parseLong(partes[1]);
                } catch (NumberFormatException error) {
                    return porDefecto;
                }
            }
        }
        return porDefecto;
    }

    private static String json(String texto) {
        StringBuilder salida = new StringBuilder("\"");
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> salida.append("\\\"");
                case '\\' -> salida.append("\\\\");
                case '\n' -> salida.append("\\n");
                case '\r' -> salida.append("\\r");
                case '\t' -> salida.append("\\t");
                default -> {
                    if (c < 0x20) {
                        salida.append(String.format("\\u%04x", (int) c));
                    } else {
                        salida.append(c);
                    }
                }
            }
        }
        return salida.append('"').toString();
    }

    private static String entorno(String nombre, String porDefecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }

    private static String nombreDelHost() {
        String delEntorno = System.getenv("HOSTNAME");
        if (delEntorno != null && !delEntorno.isBlank()) {
            return delEntorno;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException error) {
            return "desconocido";
        }
    }

    private static void dormir(long milisegundos) {
        if (milisegundos <= 0) {
            return;
        }
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static void registro(String linea) {
        System.out.println(Instant.now() + " " + linea);
    }
}
