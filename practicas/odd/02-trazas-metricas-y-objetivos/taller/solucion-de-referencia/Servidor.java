import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.api.common.AttributeKey;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Bitácora: la aplicación de práctica de los talleres de Docker y Kubernetes.
 * Un solo archivo y solo el JDK, para que la atención esté en el contenedor y no en la aplicación.
 * Toda la configuración llega por variables de entorno.
 */
public class Servidor {

    private static final AtomicBoolean listo = new AtomicBoolean(false);
    private static final AtomicLong visitas = new AtomicLong();
    private static volatile String servidorVersion = "dev";
    private static final String HOST = nombreDelHost();

    // OpenTelemetry: con el agente, GlobalOpenTelemetry es el del agente; sin él, todo esto no hace nada.
    private static final Tracer TRAZADOR = GlobalOpenTelemetry.getTracer("bitacora");
    private static final LongCounter PETICIONES = GlobalOpenTelemetry.getMeter("bitacora")
            .counterBuilder("bitacora.peticiones").setDescription("Peticiones atendidas").setUnit("{peticion}").build();
    private static final DoubleHistogram DURACION = GlobalOpenTelemetry.getMeter("bitacora")
            .histogramBuilder("bitacora.duracion").setDescription("Duración de las peticiones").setUnit("ms").build();
    private static final LongCounter NOTAS_GUARDADAS = GlobalOpenTelemetry.getMeter("bitacora")
            .counterBuilder("bitacora.notas.guardadas").setDescription("Notas guardadas").setUnit("{nota}").build();
    private static final AtomicLong secuenciaEscrituras = new AtomicLong();
    // Para el paso 7: una etiqueta de alta cardinalidad a propósito (NO se usa en producción).
    private static final boolean ETIQUETA_ALTA = "1".equals(System.getenv("ETIQUETA_ALTA_CARDINALIDAD"));

    public static void main(String[] args) throws Exception {
        int puerto = Integer.parseInt(entorno("PUERTO", "8080"));
        String mensaje = entorno("MENSAJE", "Hola desde la bitácora");
        String version = entorno("VERSION", "dev");
        String clave = entorno("CLAVE_API", "");
        long retardoArranque = Long.parseLong(entorno("RETARDO_ARRANQUE_MS", "0"));
        long esperaApagado = Long.parseLong(entorno("ESPERA_APAGADO_MS", "0"));
        // Defecto para el paso 10: las notas de más de 400 caracteres tardan este tiempo en guardarse (0 = sin defecto).
        long defectoNotaLarga = Long.parseLong(entorno("DEFECTO_NOTA_LARGA_MS", "0"));
        servidorVersion = version;
        // Defecto del paso 11: una de cada N escrituras tarda dos segundos (0 = sin defecto).
        long defectoCadaN = Long.parseLong(entorno("DEFECTO_ESCRITURA_LENTA_CADA", "0"));
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
                motivo(peticion, "no_listo");
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
                    Span lectura = TRAZADOR.spanBuilder("notas.leer").startSpan();
                    List<String> existentes;
                    try (Scope ambito = lectura.makeCurrent()) {
                        existentes = leerNotas(archivoNotas);
                        lectura.setAttribute("bitacora.notas.total", existentes.size());
                    } finally {
                        lectura.end();
                    }
                    String elementos = existentes.stream().map(Servidor::json).collect(Collectors.joining(","));
                    responder(peticion, 200, "application/json", "{\"host\":" + json(host) + ",\"notas\":[" + elementos + "]}");
                }
                case "POST" -> {
                    if (!clave.isEmpty() && !clave.equals(peticion.getRequestHeaders().getFirst("X-Clave"))) {
                        motivo(peticion, "clave_invalida");
                        responder(peticion, 401, "application/json", "{\"error\":\"clave ausente o incorrecta\"}");
                        return;
                    }
                    String nota = new String(peticion.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                            .replaceAll("\\s+", " ").trim();
                    peticion.setAttribute("nota_longitud", nota.length());
                    if (nota.isEmpty() || nota.length() > 500) {
                        motivo(peticion, "nota_invalida");
                        responder(peticion, 400, "application/json", "{\"error\":\"la nota debe tener entre 1 y 500 caracteres\"}");
                        return;
                    }
                    if (defectoNotaLarga > 0 && nota.length() > 400) {
                        dormir(defectoNotaLarga);
                    }
                    int total;
                    long secuencia = secuenciaEscrituras.incrementAndGet();
                    // Tramo manual alrededor de la escritura: atributos útiles, nunca el contenido de la nota.
                    Span escritura = TRAZADOR.spanBuilder("notas.escribir").setAttribute("bitacora.nota.longitud", nota.length())
                            .setAttribute("bitacora.escritura.secuencia", secuencia).startSpan();
                    try (Scope ambito = escritura.makeCurrent()) {
                        if (defectoCadaN > 0 && secuencia % defectoCadaN == 0) {
                            dormir(2000);
                        }
                        synchronized (Servidor.class) {
                            Files.writeString(archivoNotas, nota + System.lineSeparator(), StandardCharsets.UTF_8,
                                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            total = leerNotas(archivoNotas).size();
                        }
                        escritura.setAttribute("bitacora.notas.total", total);
                    } catch (IOException | RuntimeException error) {
                        escritura.recordException(error);
                        escritura.setStatus(StatusCode.ERROR);
                        NOTAS_GUARDADAS.add(1, Attributes.of(AttributeKey.stringKey("resultado"), "error"));
                        throw error;
                    } finally {
                        escritura.end();
                    }
                    var etiquetas = ETIQUETA_ALTA
                            ? Attributes.of(AttributeKey.stringKey("resultado"), "ok", AttributeKey.stringKey("texto_nota"), nota)
                            : Attributes.of(AttributeKey.stringKey("resultado"), "ok");
                    NOTAS_GUARDADAS.add(1, etiquetas);
                    // Evento de negocio: ocurrió algo que le importa al producto. Sin el contenido de la nota.
                    evento("nota.guardada", Map.of("correlacion", String.valueOf(peticion.getAttribute("correlacion")),
                            "longitud", nota.length(), "notas_total", total));
                    responder(peticion, 201, "application/json", "{\"guardada\":" + json(nota) + ",\"host\":" + json(host) + "}");
                }
                default -> {
                    motivo(peticion, "metodo_no_permitido");
                    responder(peticion, 405, "application/json", "{\"error\":\"método no permitido\"}");
                }
            }
        }));

        // Gasta CPU a propósito durante los milisegundos pedidos (máximo 5000). Sirve para probar el escalado.
        servidor.createContext("/api/carga", registrar(peticion -> {
            long milisegundos = Math.min(5000, Math.max(0, parametro(peticion, "ms", 200)));
            peticion.setAttribute("carga_ms", milisegundos);
            long fin = System.nanoTime() + milisegundos * 1_000_000;
            double acumulado = 0;
            while (System.nanoTime() < fin) {
                acumulado += Math.sqrt(Math.random());
            }
            responder(peticion, 200, "application/json",
                    "{\"host\":" + json(host) + ",\"ms\":" + milisegundos + ",\"resultado\":" + Math.round(acumulado) + "}");
        }));

        servidor.createContext("/", registrar(peticion -> {
            motivo(peticion, "ruta_desconocida");
            responder(peticion, 404, "application/json",
                    "{\"error\":\"ruta desconocida\",\"rutas\":[\"/salud/vivo\",\"/salud/listo\",\"/api/info\",\"/api/notas\",\"/api/carga\"]}");
        }));

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

    /** Envuelve un manejador: pone el identificador de correlación y escribe un evento JSON por petición. */
    private static HttpHandler registrar(Manejador manejador) {
        return peticion -> {
            long inicio = System.nanoTime();
            String correlacion = correlacion(peticion);
            peticion.setAttribute("correlacion", correlacion);
            peticion.getResponseHeaders().set("X-Correlacion", correlacion);
            try {
                manejador.atender(peticion);
            } catch (IOException | RuntimeException error) {
                motivo(peticion, "excepcion");
                registro("ERROR " + peticion.getRequestURI().getPath() + " correlacion=" + correlacion + ": " + error);
                if (peticion.getResponseCode() == -1) {
                    responder(peticion, 500, "application/json", "{\"error\":\"fallo interno\"}");
                }
            } finally {
                int estado = peticion.getResponseCode();
                Map<String, Object> campos = new LinkedHashMap<>();
                campos.put("correlacion", correlacion);
                campos.put("metodo", peticion.getRequestMethod());
                campos.put("ruta", peticion.getRequestURI().getPath());
                campos.put("estado", estado);
                campos.put("resultado", estado >= 500 ? "error_servidor" : estado >= 400 ? "rechazo_cliente" : "exito");
                campos.put("motivo", peticion.getAttribute("motivo"));
                campos.put("duracion_ms", (System.nanoTime() - inicio) / 1_000_000);
                campos.put("nota_longitud", peticion.getAttribute("nota_longitud"));
                campos.put("carga_ms", peticion.getAttribute("carga_ms"));
                SpanContext contexto = Span.current().getSpanContext();
                if (contexto.isValid()) {
                    // Del registro a la traza: el mismo identificador que ve la interfaz de trazas.
                    campos.put("trace_id", contexto.getTraceId());
                    campos.put("span_id", contexto.getSpanId());
                    Span.current().setAttribute("bitacora.correlacion", correlacion);
                }
                String ruta = peticion.getHttpContext().getPath();
                Attributes etiquetas = Attributes.of(AttributeKey.stringKey("ruta"), "/".equals(ruta) ? "desconocida" : ruta,
                        AttributeKey.stringKey("metodo"), peticion.getRequestMethod(),
                        AttributeKey.stringKey("clase_estado"), (estado / 100) + "xx");
                PETICIONES.add(1, etiquetas);
                DURACION.record((System.nanoTime() - inicio) / 1_000_000.0, etiquetas);
                evento("peticion", campos);
                peticion.close();
            }
        };
    }

    /** La cabecera X-Correlacion si viene y es razonable; si no, un identificador nuevo. */
    private static String correlacion(HttpExchange peticion) {
        String recibido = peticion.getRequestHeaders().getFirst("X-Correlacion");
        if (recibido != null && recibido.matches("[A-Za-z0-9._-]{1,64}")) {
            return recibido;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static void motivo(HttpExchange peticion, String motivo) {
        peticion.setAttribute("motivo", motivo);
    }

    /** Una línea JSON en la salida estándar. Los campos nulos no se escriben. */
    private static void evento(String nombre, Map<String, Object> campos) {
        StringBuilder linea = new StringBuilder("{\"ts\":").append(json(Instant.now().toString()))
                .append(",\"evento\":").append(json(nombre))
                .append(",\"servicio\":\"bitacora-api\",\"version\":").append(json(servidorVersion))
                .append(",\"host\":").append(json(HOST));
        campos.forEach((clave, valor) -> {
            if (valor == null || "null".equals(valor)) {
                return;
            }
            linea.append(',').append(json(clave)).append(':');
            linea.append(valor instanceof Number ? valor.toString() : json(valor.toString()));
        });
        System.out.println(linea.append('}'));
    }

    private static boolean rechazarSiNoEstaListo(HttpExchange peticion) throws IOException {
        if (listo.get()) {
            return false;
        }
        motivo(peticion, "no_listo");
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

    /** Mensajes del propio proceso (arranque, apagado, errores), también como JSON. */
    private static void registro(String linea) {
        evento("log", Map.of("mensaje", linea));
    }
}
