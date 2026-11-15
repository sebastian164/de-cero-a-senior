package banco;

import banco.adaptadores.entrada.consola.Consola;
import banco.adaptadores.entrada.http.ServidorHttp;
import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.salida.Reloj;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Los dos adaptadores de entrada, sobre el mismo núcleo. */
public final class PruebasDeAdaptadores {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");

    private PruebasDeAdaptadores() {
    }

    public static void ejecutar() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, almacen, RELOJ, almacen);
        try {
            porHttp(banco);
            porConsola(banco);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void porHttp(ServicioDeCuentas banco) throws IOException {
        System.out.println("Adaptador HTTP:");
        ServidorHttp servidor = new ServidorHttp(0, banco, banco, banco, banco);       // puerto 0: uno libre
        servidor.iniciar();
        String base = "http://localhost:" + servidor.puerto();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            HttpResponse<String> ana = enviar(cliente, "POST", base + "/cuentas", "titular=Ana&centavos=100000000", null);
            Comprobar.igual("abrir una cuenta responde 201", 201, ana.statusCode());
            Comprobar.igual("y devuelve la cuenta en JSON",
                    "{\"id\":\"cuenta-1\",\"titular\":\"Ana\",\"saldoCentavos\":100000000}", ana.body());
            enviar(cliente, "POST", base + "/cuentas", "titular=Luis&centavos=20000000", null);

            String transferencia = "origen=cuenta-1&destino=cuenta-2&centavos=30000000";
            Comprobar.igual("una transferencia responde 201", 201,
                    enviar(cliente, "POST", base + "/transferencias", transferencia, "orden-1").statusCode());
            Comprobar.igual("repetida con la misma clave responde 200", 200,
                    enviar(cliente, "POST", base + "/transferencias", transferencia, "orden-1").statusCode());
            Comprobar.igual("sin clave de idempotencia responde 400", 400,
                    enviar(cliente, "POST", base + "/transferencias", transferencia, null).statusCode());
            Comprobar.igual("una regla del negocio responde 422", 422,
                    enviar(cliente, "POST", base + "/transferencias", "origen=cuenta-2&destino=cuenta-1&centavos=90000000", "orden-2").statusCode());
            Comprobar.igual("una cuenta inexistente responde 404", 404,
                    enviar(cliente, "POST", base + "/transferencias", "origen=cuenta-1&destino=cuenta-9&centavos=1000", "orden-3").statusCode());
            Comprobar.igual("un número mal escrito responde 400", 400,
                    enviar(cliente, "POST", base + "/cuentas/cuenta-1/depositos", "centavos=mucho", null).statusCode());
            Comprobar.igual("el saldo se consulta por GET",
                    "{\"id\":\"cuenta-1\",\"titular\":\"Ana\",\"saldoCentavos\":70000000}",
                    enviar(cliente, "GET", base + "/cuentas/cuenta-1", null, null).body());
            Comprobar.igual("una ruta desconocida responde 404", 404,
                    enviar(cliente, "GET", base + "/prestamos", null, null).statusCode());
        } finally {
            servidor.detener();
        }
    }

    private static void porConsola(ServicioDeCuentas banco) throws IOException {
        System.out.println("Adaptador de consola, sobre el mismo banco:");
        ByteArrayOutputStream captura = new ByteArrayOutputStream();
        Consola consola = new Consola(banco, banco, banco, banco, new PrintStream(captura, true, StandardCharsets.UTF_8));
        consola.atender(new BufferedReader(new StringReader(String.join("\n",
                "saldo cuenta-1",
                "transferir orden-1 cuenta-1 cuenta-2 300000",
                "depositar cuenta-2 50000",
                "transferir orden-4 cuenta-2 cuenta-1 9000000",
                "transferir orden-5 cuenta-1",
                "bailar"))));
        String[] lineas = captura.toString(StandardCharsets.UTF_8).split("\\R");

        Comprobar.igual("ve la cuenta que se abrió por HTTP", "cuenta-1 Ana: $ 700.000,00", lineas[0]);
        Comprobar.igual("la orden enviada por HTTP figura como atendida", "esa orden ya se había atendido", lineas[1]);
        Comprobar.igual("depositar muestra el saldo nuevo", "cuenta-2 Luis: $ 550.000,00", lineas[2]);
        Comprobar.igual("un rechazo del núcleo se muestra como texto", "rechazada: saldo insuficiente en cuenta-2", lineas[3]);
        Comprobar.igual("una línea incompleta muestra el uso", true, lineas[4].startsWith("uso:"));
        Comprobar.igual("una orden desconocida no rompe nada", "no entiendo: bailar", lineas[5]);
    }

    private static HttpResponse<String> enviar(HttpClient cliente, String metodo, String url, String cuerpo, String clave) throws IOException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (clave != null) {
            peticion.header("Idempotency-Key", clave);
        }
        try {
            return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
    }
}
