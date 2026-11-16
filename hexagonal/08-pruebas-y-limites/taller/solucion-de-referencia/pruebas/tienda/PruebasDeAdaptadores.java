package tienda;

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
import java.util.List;
import java.util.Optional;
import tienda.adaptadores.entrada.consola.Consola;
import tienda.adaptadores.entrada.http.ServidorHttp;
import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.puertos.salida.Catalogo;
import tienda.dominio.Dinero;

/** Los dos adaptadores de entrada con peticiones reales, sobre el mismo núcleo con memoria. */
public final class PruebasDeAdaptadores {

    private PruebasDeAdaptadores() {
    }

    public static void ejecutar() {
        try {
            porHttpYConsola();
            traduccionesQueFaltan();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void porHttpYConsola() throws IOException {
        System.out.println("Adaptador HTTP:");
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        Datos.sembrar(almacen, almacen);
        ServicioDePedidos tienda = new ServicioDePedidos(almacen, Datos.catalogo(), almacen, almacen, almacen, Datos.RELOJ, almacen);
        ServidorHttp servidor = new ServidorHttp(0, tienda, tienda, tienda, tienda, tienda, tienda);
        servidor.iniciar();
        String base = "http://127.0.0.1:" + servidor.puerto();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            HttpResponse<String> creado = enviar(cliente, "POST", base + "/pedidos", "cliente=ana&linea=teclado:2&linea=raton:1", "k-1", false);
            Comprobar.igual("crear responde 201", 201, creado.statusCode());
            Comprobar.igual("y devuelve el pedido en JSON",
                    "{\"id\":\"pedido-1\",\"cliente\":\"ana\",\"estado\":\"PENDIENTE\",\"totalCentavos\":36000000,\"lineas\":"
                            + "[{\"producto\":\"teclado\",\"cantidad\":2,\"precioUnitarioCentavos\":15000000},"
                            + "{\"producto\":\"raton\",\"cantidad\":1,\"precioUnitarioCentavos\":6000000}]}", creado.body());
            HttpResponse<String> repetido = enviar(cliente, "POST", base + "/pedidos", "cliente=ana&linea=teclado:2&linea=raton:1", "k-1", false);
            Comprobar.igual("la misma petición repetida responde 200 con el mismo pedido", List.of(200, creado.body()),
                    List.of(repetido.statusCode(), repetido.body()));
            Comprobar.igual("sin clave de idempotencia responde 400", 400,
                    enviar(cliente, "POST", base + "/pedidos", "cliente=ana&linea=raton:1", null, false).statusCode());
            Comprobar.igual("una regla del negocio responde 422", 422,
                    enviar(cliente, "POST", base + "/pedidos", "cliente=ana&linea=raton:9", "k-2", false).statusCode());
            Comprobar.igual("un producto desconocido responde 422", 422,
                    enviar(cliente, "POST", base + "/pedidos", "cliente=ana&linea=lampara:1", "k-3", false).statusCode());
            Comprobar.igual("una cantidad que no es número responde 400", 400,
                    enviar(cliente, "POST", base + "/pedidos", "cliente=ana&linea=raton:dos", "k-4", false).statusCode());
            Comprobar.igual("un pedido inexistente responde 404", 404,
                    enviar(cliente, "GET", base + "/pedidos/pedido-99", null, null, false).statusCode());
            Comprobar.igual("confirmar uno inexistente responde 404", 404,
                    enviar(cliente, "POST", base + "/pedidos/pedido-99/confirmacion", "", null, false).statusCode());
            Comprobar.igual("confirmar responde 200", 200,
                    enviar(cliente, "POST", base + "/pedidos/pedido-1/confirmacion", "", null, false).statusCode());
            Comprobar.igual("confirmar dos veces es una regla del negocio: 422", 422,
                    enviar(cliente, "POST", base + "/pedidos/pedido-1/confirmacion", "", null, false).statusCode());
            HttpResponse<String> json = enviar(cliente, "POST", base + "/pedidos",
                    "{\"cliente\":\"luis\",\"lineas\":[{\"producto\":\"monitor\",\"cantidad\":1}]}", "k-5", true);
            Comprobar.igual("crear con JSON también responde 201", 201, json.statusCode());
            Comprobar.igual("una ruta desconocida responde 404", 404, enviar(cliente, "GET", base + "/clientes", null, null, false).statusCode());
            Comprobar.igual("listar sin cliente responde 400", 400, enviar(cliente, "GET", base + "/pedidos", null, null, false).statusCode());

            System.out.println("Adaptador de consola, sobre el mismo núcleo:");
            ByteArrayOutputStream captura = new ByteArrayOutputStream();
            Consola consola = new Consola(tienda, tienda, tienda, tienda, tienda, new PrintStream(captura, true, StandardCharsets.UTF_8));
            consola.atender(new BufferedReader(new StringReader(String.join("\n",
                    "ver pedido-1",
                    "crear k-1 ana teclado:2 raton:1",
                    "crear k-6 marta monitor:1",
                    "cancelar pedido-4",
                    "crear k-7 marta monitor:5",
                    "crear k-8 marta",
                    "crear k-9",
                    "bailar"))));
            String[] lineas = captura.toString(StandardCharsets.UTF_8).split("\\R");
            Comprobar.igual("ve el pedido creado y confirmado por HTTP", "pedido-1 de ana: CONFIRMADO $ 360.000,00", lineas[0]);
            Comprobar.igual("la clave usada por HTTP figura como atendida", "ya existía pedido-1 de ana: CONFIRMADO $ 360.000,00", lineas[1]);
            Comprobar.igual("crea un pedido", "creado pedido-4 de marta: PENDIENTE $ 900.000,00", lineas[2]);
            Comprobar.igual("lo cancela", "cancelado pedido-4 de marta: CANCELADO $ 900.000,00", lineas[3]);
            Comprobar.igual("un rechazo del núcleo se muestra como texto", "rechazado: no hay existencias suficientes de monitor", lineas[4]);
            Comprobar.igual("sin líneas también es del núcleo", "rechazado: un pedido necesita al menos una línea", lineas[5]);
            Comprobar.igual("una orden incompleta muestra el uso", true, lineas[6].startsWith("uso:"));
            Comprobar.igual("una orden desconocida no rompe nada", "no entiendo: bailar", lineas[7]);

            HttpResponse<String> grande = enviar(cliente, "POST", base + "/pedidos", "cliente=eva&linea=teclado:11", "k-grande", false);
            Comprobar.igual("sin existencias para once teclados: 422", 422, grande.statusCode());
            Comprobar.igual("aprobar uno que no necesita aprobación: 422", 422,
                    enviar(cliente, "POST", base + "/pedidos/pedido-1/aprobacion", "", null, false).statusCode());
            Comprobar.igual("y HTTP ve el pedido creado por la consola", true,
                    enviar(cliente, "GET", base + "/pedidos?cliente=marta", null, null, false).body().contains("\"id\":\"pedido-4\",\"cliente\":\"marta\",\"estado\":\"CANCELADO\""));
        } finally {
            servidor.detener();
        }
    }

    /** 409 y 500: un conflicto que no cede y un fallo técnico de un adaptador de salida. */
    private static void traduccionesQueFaltan() throws IOException {
        System.out.println("Adaptador HTTP, conflicto y error interno:");
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        Datos.sembrar(almacen, almacen);
        InventarioConInterferencia inventario = new InventarioConInterferencia(almacen);
        ServicioDePedidos conflictiva = new ServicioDePedidos(almacen, Datos.catalogo(), inventario, almacen, almacen, Datos.RELOJ, almacen);
        int[] otros = {0};
        inventario.interferir(100, () -> conflictiva.crear(Datos.solicitud("otro-" + (++otros[0]), "luis", "teclado:1")));
        Catalogo roto = producto -> {
            throw new IllegalStateException("el catálogo no responde: " + producto);
        };
        ServicioDePedidos sinCatalogo = new ServicioDePedidos(almacen, roto, almacen, almacen, almacen, Datos.RELOJ, almacen);
        ServidorHttp uno = new ServidorHttp(0, conflictiva, conflictiva, conflictiva, conflictiva, conflictiva, conflictiva);
        ServidorHttp dos = new ServidorHttp(0, sinCatalogo, sinCatalogo, sinCatalogo, sinCatalogo, sinCatalogo, sinCatalogo);
        uno.iniciar();
        dos.iniciar();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            Comprobar.igual("un conflicto que no cede responde 409", 409,
                    enviar(cliente, "POST", "http://127.0.0.1:" + uno.puerto() + "/pedidos", "cliente=ana&linea=teclado:1", "k-1", false).statusCode());
            HttpResponse<String> interno = enviar(cliente, "POST", "http://127.0.0.1:" + dos.puerto() + "/pedidos", "cliente=ana&linea=teclado:1", "k-2", false);
            Comprobar.igual("un fallo técnico responde 500 sin detalles", List.of(500, "{\"error\":\"error interno\"}"),
                    List.of(interno.statusCode(), interno.body()));
        } finally {
            uno.detener();
            dos.detener();
        }
        Comprobar.igual("ninguna petición rechazada tocó las existencias de más", Optional.of(10 - otros[0]),
                almacen.existenciasDe("teclado").map(existencias -> existencias.unidades()));
        Comprobar.igual("el precio del catálogo es Dinero", new Dinero(15_000_000), Datos.catalogo().precioDe("teclado").orElseThrow());
    }

    private static HttpResponse<String> enviar(HttpClient cliente, String metodo, String url, String cuerpo, String clave, boolean json) throws IOException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", json ? "application/json" : "application/x-www-form-urlencoded")
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
