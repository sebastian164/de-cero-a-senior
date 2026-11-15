package banco.adaptadores.entrada.http;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.entrada.AbrirCuenta;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.puertos.entrada.Depositar;
import banco.aplicacion.puertos.entrada.TransferirDinero;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.dominio.Movimiento;
import banco.dominio.ReglaDeNegocioViolada;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Traduce peticiones HTTP a llamadas a los puertos de entrada, y sus resultados a JSON. Habla en centavos. */
public final class ServidorHttp {

    private record Respuesta(int estado, String cuerpo) {
    }

    private final HttpServer servidor;
    private final AbrirCuenta abrirCuenta;
    private final Depositar depositar;
    private final TransferirDinero transferirDinero;
    private final ConsultarCuenta consultarCuenta;

    public ServidorHttp(int puerto, AbrirCuenta abrirCuenta, Depositar depositar, TransferirDinero transferirDinero,
                        ConsultarCuenta consultarCuenta) throws IOException {
        this.abrirCuenta = abrirCuenta;
        this.depositar = depositar;
        this.transferirDinero = transferirDinero;
        this.consultarCuenta = consultarCuenta;
        this.servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        this.servidor.createContext("/", this::atender);
    }

    public void iniciar() {
        servidor.start();
    }

    public void detener() {
        servidor.stop(0);
    }

    public int puerto() {
        return servidor.getAddress().getPort();
    }

    private void atender(HttpExchange intercambio) throws IOException {
        Respuesta respuesta;
        try {
            respuesta = despachar(intercambio);
        } catch (ReglaDeNegocioViolada rechazo) {
            respuesta = error(422, rechazo.getMessage());                // el núcleo dijo que no
        } catch (CuentaNoEncontrada ausente) {
            respuesta = error(404, ausente.getMessage());
        } catch (ConflictoDeConcurrencia conflicto) {
            respuesta = error(409, conflicto.getMessage());              // puede reintentar con la misma clave
        } catch (IllegalArgumentException malFormada) {
            respuesta = error(400, malFormada.getMessage());
        } catch (RuntimeException inesperado) {
            respuesta = error(500, "error interno");                     // sin detalles hacia fuera
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
        String[] ruta = intercambio.getRequestURI().getPath().split("/");    // "/cuentas/cuenta-1" -> "", "cuentas", "cuenta-1"
        Map<String, String> datos = formulario(intercambio);

        if (metodo.equals("POST") && es(ruta, "cuentas")) {
            VistaDeCuenta cuenta = abrirCuenta.abrir(exigir(datos, "titular"), numero(datos, "centavos"));
            return new Respuesta(201, json(cuenta));
        }
        if (metodo.equals("POST") && es(ruta, "cuentas", "*", "depositos")) {
            return new Respuesta(200, json(depositar.depositar(ruta[2], numero(datos, "centavos"))));
        }
        if (metodo.equals("POST") && es(ruta, "transferencias")) {
            String clave = intercambio.getRequestHeaders().getFirst("Idempotency-Key");
            if (clave == null || clave.isBlank()) {
                throw new IllegalArgumentException("falta la cabecera Idempotency-Key");
            }
            Resultado resultado = transferirDinero.transferir(
                    new Orden(clave, exigir(datos, "origen"), exigir(datos, "destino"), numero(datos, "centavos")));
            int estado = resultado == Resultado.REALIZADA ? 201 : 200;   // repetida: éxito, sin crear nada nuevo
            return new Respuesta(estado, "{\"resultado\":" + texto(resultado.name()) + "}");
        }
        if (metodo.equals("GET") && es(ruta, "cuentas", "*")) {
            return consultarCuenta.porId(ruta[2]).map(cuenta -> new Respuesta(200, json(cuenta)))
                    .orElseThrow(() -> new CuentaNoEncontrada(ruta[2]));
        }
        if (metodo.equals("GET") && es(ruta, "cuentas", "*", "movimientos")) {
            String movimientos = consultarCuenta.movimientosDe(ruta[2]).stream().map(ServidorHttp::json)
                    .collect(Collectors.joining(",", "[", "]"));
            return new Respuesta(200, movimientos);
        }
        return error(404, "ruta desconocida");
    }

    /** ¿La ruta tiene esta forma? El asterisco acepta cualquier tramo. */
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

    private static Map<String, String> formulario(HttpExchange intercambio) throws IOException {
        String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> datos = new HashMap<>();
        for (String par : cuerpo.split("&")) {
            String[] partes = par.split("=", 2);
            if (partes.length == 2) {
                datos.put(URLDecoder.decode(partes[0], StandardCharsets.UTF_8), URLDecoder.decode(partes[1], StandardCharsets.UTF_8));
            }
        }
        return datos;
    }

    private static String exigir(Map<String, String> datos, String campo) {
        String valor = datos.get(campo);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("falta el campo " + campo);
        }
        return valor;
    }

    private static long numero(Map<String, String> datos, String campo) {
        try {
            return Long.parseLong(exigir(datos, campo));
        } catch (NumberFormatException noEsNumero) {
            throw new IllegalArgumentException("el campo " + campo + " debe ser un número entero");
        }
    }

    // El JSON se arma a mano para no depender de nada. En un proyecto real lo hace una biblioteca.

    private static String json(VistaDeCuenta cuenta) {
        return "{\"id\":" + texto(cuenta.id()) + ",\"titular\":" + texto(cuenta.titular())
                + ",\"saldoCentavos\":" + cuenta.saldoCentavos() + "}";
    }

    private static String json(Movimiento movimiento) {
        return "{\"tipo\":" + texto(movimiento.tipo().name()) + ",\"centavos\":" + movimiento.valor().centavos()
                + ",\"fecha\":" + texto(movimiento.fecha().toString()) + ",\"referencia\":" + texto(movimiento.referencia()) + "}";
    }

    private static Respuesta error(int estado, String mensaje) {
        return new Respuesta(estado, "{\"error\":" + texto(mensaje) + "}");
    }

    private static String texto(String valor) {
        return "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
