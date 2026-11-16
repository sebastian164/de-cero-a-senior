package tienda.adaptadores.entrada.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.aplicacion.VistaDePedido;
import tienda.aplicacion.puertos.entrada.AprobarPedido;
import tienda.aplicacion.puertos.entrada.CancelarPedido;
import tienda.aplicacion.puertos.entrada.ConfirmarPedido;
import tienda.aplicacion.puertos.entrada.ConsultarPedido;
import tienda.aplicacion.puertos.entrada.CrearPedido;
import tienda.aplicacion.puertos.entrada.CrearPedido.Linea;
import tienda.aplicacion.puertos.entrada.CrearPedido.Resultado;
import tienda.aplicacion.puertos.entrada.CrearPedido.Solicitud;
import tienda.aplicacion.puertos.entrada.ListarPedidosDeCliente;
import tienda.dominio.ReglaDeNegocioViolada;

/**
 * Traduce HTTP a los puertos de entrada y sus resultados a JSON. Habla en centavos.
 * Crear acepta formulario (cliente=…&linea=teclado:2&linea=raton:1) o JSON ({"cliente":…,"lineas":[{"producto":…,"cantidad":…}]}).
 */
public final class ServidorHttp {

    private record Respuesta(int estado, String cuerpo) {
    }

    private static final Pattern CLIENTE = Pattern.compile("\"cliente\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern LINEA = Pattern.compile(
            "\\{\\s*\"producto\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"cantidad\"\\s*:\\s*(-?\\d+)\\s*}");

    private final HttpServer servidor;
    private final ExecutorService hilos = Executors.newFixedThreadPool(8);
    private final CrearPedido crearPedido;
    private final AprobarPedido aprobarPedido;
    private final ConfirmarPedido confirmarPedido;
    private final CancelarPedido cancelarPedido;
    private final ConsultarPedido consultarPedido;
    private final ListarPedidosDeCliente listarPedidos;

    public ServidorHttp(int puerto, CrearPedido crearPedido, AprobarPedido aprobarPedido, ConfirmarPedido confirmarPedido,
                        CancelarPedido cancelarPedido, ConsultarPedido consultarPedido, ListarPedidosDeCliente listarPedidos) throws IOException {
        this.crearPedido = crearPedido;
        this.aprobarPedido = aprobarPedido;
        this.confirmarPedido = confirmarPedido;
        this.cancelarPedido = cancelarPedido;
        this.consultarPedido = consultarPedido;
        this.listarPedidos = listarPedidos;
        this.servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", puerto), 0);
        this.servidor.createContext("/", this::atender);
        this.servidor.setExecutor(hilos);                        // varias peticiones a la vez, como un servidor real
    }

    public void iniciar() {
        servidor.start();
    }

    public void detener() {
        servidor.stop(0);
        hilos.shutdownNow();
    }

    public int puerto() {
        return servidor.getAddress().getPort();
    }

    private void atender(HttpExchange intercambio) throws IOException {
        Respuesta respuesta;
        try {
            respuesta = despachar(intercambio);
        } catch (ReglaDeNegocioViolada rechazo) {
            respuesta = error(422, rechazo.getMessage());
        } catch (PedidoNoEncontrado ausente) {
            respuesta = error(404, ausente.getMessage());
        } catch (ConflictoDeConcurrencia conflicto) {
            respuesta = error(409, conflicto.getMessage());
        } catch (IllegalArgumentException malFormada) {
            respuesta = error(400, malFormada.getMessage());
        } catch (RuntimeException inesperado) {
            respuesta = error(500, "error interno");
        }
        byte[] cuerpo = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
        try (intercambio) {
            intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            intercambio.sendResponseHeaders(respuesta.estado(), cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
        }
    }

    private Respuesta despachar(HttpExchange intercambio) throws IOException {
        String metodo = intercambio.getRequestMethod();
        String[] ruta = intercambio.getRequestURI().getPath().split("/");
        String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        if (metodo.equals("POST") && es(ruta, "pedidos")) {
            String clave = intercambio.getRequestHeaders().getFirst("Idempotency-Key");
            if (clave == null || clave.isBlank()) {
                throw new IllegalArgumentException("falta la cabecera Idempotency-Key");
            }
            String tipo = intercambio.getRequestHeaders().getFirst("Content-Type");
            Solicitud solicitud = tipo != null && tipo.startsWith("application/json") ? desdeJson(clave, cuerpo) : desdeFormulario(clave, cuerpo);
            Resultado resultado = crearPedido.crear(solicitud);
            return new Respuesta(resultado.nuevo() ? 201 : 200, json(resultado.pedido()));   // repetida: éxito, sin crear nada
        }
        if (metodo.equals("POST") && es(ruta, "pedidos", "*", "aprobacion")) {
            return new Respuesta(200, json(aprobarPedido.aprobar(ruta[2])));
        }
        if (metodo.equals("POST") && es(ruta, "pedidos", "*", "confirmacion")) {
            return new Respuesta(200, json(confirmarPedido.confirmar(ruta[2])));
        }
        if (metodo.equals("POST") && es(ruta, "pedidos", "*", "cancelacion")) {
            return new Respuesta(200, json(cancelarPedido.cancelar(ruta[2])));
        }
        if (metodo.equals("GET") && es(ruta, "pedidos", "*")) {
            return consultarPedido.porId(ruta[2]).map(pedido -> new Respuesta(200, json(pedido)))
                    .orElseThrow(() -> new PedidoNoEncontrado(ruta[2]));
        }
        if (metodo.equals("GET") && es(ruta, "pedidos")) {
            String consulta = intercambio.getRequestURI().getRawQuery();
            String cliente = consulta != null && consulta.startsWith("cliente=") ? decodificar(consulta.substring("cliente=".length())) : "";
            if (cliente.isBlank()) {
                throw new IllegalArgumentException("falta el parámetro cliente");
            }
            return new Respuesta(200, listarPedidos.deCliente(cliente).stream().map(ServidorHttp::json).collect(Collectors.joining(",", "[", "]")));
        }
        return error(404, "ruta desconocida");
    }

    private static Solicitud desdeFormulario(String clave, String cuerpo) {
        String cliente = "";
        List<Linea> lineas = new ArrayList<>();
        for (String par : cuerpo.split("&")) {
            String[] partes = par.split("=", 2);
            if (partes.length == 2 && partes[0].equals("cliente")) {
                cliente = decodificar(partes[1]);
            }
            if (partes.length == 2 && partes[0].equals("linea")) {
                String[] productoCantidad = decodificar(partes[1]).split(":");
                if (productoCantidad.length != 2) {
                    throw new IllegalArgumentException("una línea es producto:cantidad");
                }
                lineas.add(new Linea(productoCantidad[0], entero(productoCantidad[1])));
            }
        }
        return new Solicitud(clave, cliente, lineas);
    }

    private static Solicitud desdeJson(String clave, String cuerpo) {
        Matcher cliente = CLIENTE.matcher(cuerpo);
        if (!cliente.find()) {
            throw new IllegalArgumentException("falta el campo cliente");
        }
        List<Linea> lineas = new ArrayList<>();
        Matcher linea = LINEA.matcher(cuerpo);
        while (linea.find()) {
            lineas.add(new Linea(linea.group(1), entero(linea.group(2))));
        }
        return new Solicitud(clave, cliente.group(1), lineas);
    }

    private static int entero(String texto) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException noEsNumero) {
            throw new IllegalArgumentException("la cantidad debe ser un número entero: " + texto);
        }
    }

    private static String decodificar(String texto) {
        return URLDecoder.decode(texto, StandardCharsets.UTF_8);
    }

    private static boolean es(String[] ruta, String... forma) {
        if (ruta.length != forma.length + 1) {
            return false;
        }
        for (int i = 0; i < forma.length; i++) {
            if (!forma[i].equals("*") && !forma[i].equals(ruta[i + 1])) {
                return false;
            }
        }
        return true;
    }

    private static String json(VistaDePedido pedido) {
        String lineas = pedido.lineas().stream()
                .map(linea -> "{\"producto\":" + texto(linea.producto()) + ",\"cantidad\":" + linea.cantidad()
                        + ",\"precioUnitarioCentavos\":" + linea.precioUnitarioCentavos() + "}")
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"id\":" + texto(pedido.id()) + ",\"cliente\":" + texto(pedido.cliente()) + ",\"estado\":" + texto(pedido.estado())
                + ",\"totalCentavos\":" + pedido.totalCentavos() + ",\"lineas\":" + lineas + "}";
    }

    private static Respuesta error(int estado, String mensaje) {
        return new Respuesta(estado, "{\"error\":" + texto(mensaje) + "}");
    }

    private static String texto(String valor) {
        return "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
