# 06 — Adaptadores y composición

## Idea

Un adaptador de viaje. Tu cargador es el mismo en cualquier país; lo que cambia es la pieza que va entre el cargador y la pared. Esa pieza no carga nada ni decide nada: solo hace que dos formas distintas encajen. Si viajas a otro país, cambias la pieza, no el cargador.

En el banco, el cargador es el núcleo. HTTP, la consola, la base de datos y el broker son paredes distintas.

## Lo esencial

### Dos clases de adaptador

| | De entrada | De salida |
|---|---|---|
| Quién lo llama | El mundo exterior | La aplicación |
| A quién llama | A un puerto de entrada | Al mundo exterior |
| Respecto al puerto | Lo **usa** | Lo **implementa** |
| Ejemplos | Servidor HTTP, consola, consumidor de Kafka, tarea programada | Repositorio, cliente de otro servicio, publicador, reloj |

### Qué hace un adaptador de entrada

Traduce. Nada más.

| Hace | No hace |
|---|---|
| Lee la petición en su formato (JSON, formulario, línea de texto) | Decidir si hay saldo |
| Valida el **formato**: que un número sea un número | Validar reglas del negocio |
| Llama a un puerto de entrada con datos simples | Abrir transacciones |
| Traduce el resultado y los errores a su formato | Llamar a otro adaptador |

Una prueba rápida: si borras el adaptador HTTP y escribes otro, ¿hay que reescribir alguna regla? Si la respuesta es sí, la regla estaba en el sitio equivocado.

### La traducción de errores

El núcleo lanza excepciones con el vocabulario del negocio. Cada adaptador decide cómo se ven en su mundo.

| Excepción del núcleo | HTTP | Consola |
|---|---|---|
| `ReglaDeNegocioViolada` | 422 | `rechazada: …` |
| `CuentaNoEncontrada` | 404 | `rechazada: …` |
| `ConflictoDeConcurrencia` | 409 | `rechazada: …` |
| Entrada mal formada | 400 | `uso: …` |
| Cualquier otra | 500, sin detalles | — |

El núcleo no sabe qué es un 422. Y el adaptador no sabe por qué se rechazó: solo que fue una regla del negocio.

### Las unidades también se traducen

El núcleo trabaja en centavos. La consola habla con personas y usa pesos; la API habla con programas y usa centavos, como hacen las API de pagos. Cada adaptador convierte en su borde.

### La raíz de composición

Alguien tiene que crear los adaptadores y conectarlos a los puertos. Ese sitio se llama raíz de composición y es **el único que conoce todas las piezas**.

| Regla | Por qué |
|---|---|
| Hay una sola, en el arranque | Cambiar una tecnología es cambiar ese archivo |
| Es el único sitio con `new` de adaptadores | Nadie más depende de clases concretas |
| El núcleo recibe todo por constructor | Se puede armar igual en producción y en una prueba |

Las pruebas de las sesiones 03 a 05 ya eran raíces de composición: armaban el mismo servicio con otros adaptadores.

## El código

### Un adaptador de salida trivial

<!-- file: ../proyecto/src/banco/adaptadores/salida/sistema/RelojDelSistema.java -->
```java
package banco.adaptadores.salida.sistema;

import banco.aplicacion.puertos.salida.Reloj;
import java.time.Instant;

public final class RelojDelSistema implements Reloj {

    @Override
    public Instant ahora() {
        return Instant.now();
    }
}
```

### Adaptador de entrada: consola

<!-- file: ../proyecto/src/banco/adaptadores/entrada/consola/Consola.java -->
```java
package banco.adaptadores.entrada.consola;

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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Locale;
import java.util.stream.Collectors;

/** Traduce líneas de texto a llamadas a los puertos de entrada, y sus resultados a texto. Habla en pesos. */
public final class Consola {

    private static final String USO = "uso: abrir <titular> <pesos> | depositar <cuenta> <pesos> | "
            + "transferir <clave> <origen> <destino> <pesos> | saldo <cuenta> | extracto <cuenta>";

    private final AbrirCuenta abrirCuenta;
    private final Depositar depositar;
    private final TransferirDinero transferirDinero;
    private final ConsultarCuenta consultarCuenta;
    private final PrintStream salida;

    public Consola(AbrirCuenta abrirCuenta, Depositar depositar, TransferirDinero transferirDinero,
                   ConsultarCuenta consultarCuenta, PrintStream salida) {
        this.abrirCuenta = abrirCuenta;
        this.depositar = depositar;
        this.transferirDinero = transferirDinero;
        this.consultarCuenta = consultarCuenta;
        this.salida = salida;
    }

    public void atender(BufferedReader entrada) throws IOException {
        String linea;
        while ((linea = entrada.readLine()) != null) {
            if (!linea.isBlank()) {
                salida.println(ejecutar(linea.trim()));
            }
        }
    }

    private String ejecutar(String linea) {
        String[] partes = linea.split("\\s+");
        try {
            return switch (partes[0]) {
                case "abrir" -> "abierta " + describir(abrirCuenta.abrir(partes[1], centavos(partes[2])));
                case "depositar" -> describir(depositar.depositar(partes[1], centavos(partes[2])));
                case "transferir" -> {
                    Resultado resultado = transferirDinero.transferir(new Orden(partes[1], partes[2], partes[3], centavos(partes[4])));
                    yield resultado == Resultado.REALIZADA ? "transferencia realizada" : "esa orden ya se había atendido";
                }
                case "saldo" -> consultarCuenta.porId(partes[1]).map(Consola::describir).orElse("no existe la cuenta " + partes[1]);
                case "extracto" -> consultarCuenta.movimientosDe(partes[1]).stream().map(Consola::describir)
                        .collect(Collectors.joining(System.lineSeparator()));
                default -> "no entiendo: " + linea;
            };
        } catch (ReglaDeNegocioViolada | CuentaNoEncontrada | ConflictoDeConcurrencia rechazo) {
            return "rechazada: " + rechazo.getMessage();                 // el núcleo dijo que no
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException malFormada) {
            return USO;                                                  // la línea no tenía la forma esperada
        }
    }

    private static long centavos(String pesos) {
        return Math.multiplyExact(Long.parseLong(pesos), 100L);
    }

    private static String describir(VistaDeCuenta cuenta) {
        return cuenta.id() + " " + cuenta.titular() + ": " + pesos(cuenta.saldoCentavos());
    }

    private static String describir(Movimiento movimiento) {
        return movimiento.fecha() + " " + movimiento.tipo() + " " + pesos(movimiento.valor().centavos()) + " (" + movimiento.referencia() + ")";
    }

    private static String pesos(long centavos) {
        String enteros = String.format(Locale.ROOT, "%,d", centavos / 100).replace(',', '.');
        return "$ " + enteros + String.format(Locale.ROOT, ",%02d", centavos % 100);
    }
}
```

### Adaptador de entrada: HTTP

Con el servidor HTTP del JDK, para no depender de nada. Los cuerpos llegan como formulario (`clave=valor&…`) y las respuestas salen en JSON.

| Método y ruta | Puerto de entrada |
|---|---|
| `POST /cuentas` | `AbrirCuenta` |
| `POST /cuentas/{id}/depositos` | `Depositar` |
| `POST /transferencias`, con cabecera `Idempotency-Key` | `TransferirDinero` |
| `GET /cuentas/{id}` | `ConsultarCuenta` |
| `GET /cuentas/{id}/movimientos` | `ConsultarCuenta` |

<!-- file: ../proyecto/src/banco/adaptadores/entrada/http/ServidorHttp.java -->
```java
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
```

Busca en ese archivo la palabra "saldo" como regla, o "tope": no está. Lo único que sabe del negocio es qué puerto llamar.

### La raíz de composición

<!-- file: ../proyecto/src/banco/Main.java -->
```java
package banco;

import banco.adaptadores.entrada.consola.Consola;
import banco.adaptadores.entrada.http.ServidorHttp;
import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.adaptadores.salida.sistema.RelojDelSistema;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.salida.Reloj;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** La raíz de composición: el único sitio que conoce todas las piezas y las conecta. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        String modo = args.length > 0 ? args[0] : "consola";

        // 1. Adaptadores de salida.
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        Reloj reloj = new RelojDelSistema();

        // 2. La aplicación. Recibe puertos; no sabe qué hay detrás.
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, almacen, reloj, almacen);

        // 3. El mensajero de la bandeja de salida. Aquí el destino es la pantalla; en producción, un broker.
        Consumer<EventoDeCuenta> destino = evento -> System.out.println("[evento] " + evento.tipo() + " " + evento.id());
        ScheduledExecutorService mensajero = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "mensajero");
            hilo.setDaemon(true);
            return hilo;
        });
        mensajero.scheduleWithFixedDelay(() -> entregar(almacen, destino), 200, 200, TimeUnit.MILLISECONDS);

        // 4. Un adaptador de entrada. Los dos usan los mismos casos de uso.
        switch (modo) {
            case "http" -> {
                int puerto = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
                ServidorHttp servidor = new ServidorHttp(puerto, banco, banco, banco, banco);
                servidor.iniciar();
                System.out.println("Banco en http://localhost:" + servidor.puerto());
            }
            case "consola" -> {
                Consola consola = new Consola(banco, banco, banco, banco, System.out);
                consola.atender(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
                entregar(almacen, destino);
            }
            default -> System.out.println("uso: java -cp salida banco.Main [consola | http [puerto]]");
        }
    }

    private static void entregar(AlmacenEnMemoria almacen, Consumer<EventoDeCuenta> destino) {
        try {
            almacen.entregarPendientes(destino);
        } catch (RuntimeException fallo) {
            System.err.println("entrega de eventos pendiente: " + fallo.getMessage());   // se reintenta en la siguiente vuelta
        }
    }
}
```

`banco` aparece cuatro veces en cada constructor de adaptador de entrada porque `ServicioDeCuentas` implementa los cuatro puertos. Cada adaptador pide solo lo que usa; que hoy sea el mismo objeto es una decisión de este archivo.

### Probar los adaptadores

Los adaptadores de entrada se prueban con peticiones reales, contra el núcleo real con memoria.

<!-- file: ../proyecto/pruebas/banco/PruebasDeAdaptadores.java -->
```java
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
```

### Ejecutarlo

Desde `hexagonal/proyecto/`:

```bash
javac -Xlint:all -Werror -d salida $(find src pruebas -name '*.java')

# Consola
printf 'abrir Ana 1000000\nabrir Luis 200000\ntransferir orden-1 cuenta-1 cuenta-2 300000\nextracto cuenta-1\n' \
  | java -cp salida banco.Main consola

# HTTP, en otra terminal
java -cp salida banco.Main http 8080
curl -s -X POST localhost:8080/cuentas -d 'titular=Ana&centavos=100000000'
curl -s -X POST localhost:8080/cuentas -d 'titular=Luis&centavos=20000000'
curl -s -X POST localhost:8080/transferencias -H 'Idempotency-Key: orden-1' \
  -d 'origen=cuenta-1&destino=cuenta-2&centavos=30000000'
curl -s localhost:8080/cuentas/cuenta-1
```

Envía la transferencia dos veces con la misma clave y mira el saldo. En la terminal del servidor verás el evento que entrega el mensajero.

## Errores comunes

- Reglas del negocio en el controlador "porque ahí ya tengo los datos".
- Devolver entidades del dominio directamente en la respuesta: el formato de la API queda atado al modelo interno.
- Excepciones de la tecnología que cruzan hacia el núcleo, o excepciones del núcleo que salen sin traducir.
- Un adaptador que llama a otro adaptador saltándose la aplicación.
- Crear adaptadores con `new` dentro del núcleo.
- Varias raíces de composición repartidas por el código.
- Probar los adaptadores solo con dobles de la aplicación y no saber si el conjunto funciona.
- Devolver error cuando llega una orden repetida.

## Taller

**Objetivo:** exponer tu servicio de pedidos por dos adaptadores de entrada y conectarlo todo en una raíz de composición.

Sobre tu servicio de la sesión 05. El punto de partida de la sesión 01 ya traía un servidor HTTP; ahora lo reescribes como adaptador.

1. Escribe el adaptador HTTP. Como mínimo: crear un pedido, confirmarlo, cancelarlo y consultarlo. La creación recibe la clave de idempotencia en una cabecera.
2. Escribe la tabla de traducción de tus excepciones a códigos de estado, y justifica cada fila en una línea.
3. Escribe un segundo adaptador de entrada: consola, o una tarea que cancele los pedidos sin confirmar tras cierto tiempo. Debe usar los mismos puertos.
4. Escribe la raíz de composición. Debe ser el único archivo que crea adaptadores.
5. Compara con `Pedidos.java` de la sesión 01: toma una regla cualquiera y muestra dónde estaba y dónde está ahora.
6. Prueba el adaptador HTTP con peticiones reales contra el núcleo con memoria: un camino feliz, un rechazo por regla, un recurso inexistente, una entrada mal formada y una petición repetida.
7. Prueba que un pedido creado por un adaptador se ve desde el otro.
8. Cambia el formato de entrada de HTTP, por ejemplo de formulario a JSON o al revés. ¿Qué archivos tocaste?
9. Busca en tus adaptadores de entrada cualquier comparación con valores del negocio. Si hay alguna, muévela.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Los adaptadores de entrada solo dependen de puertos de entrada | Búsqueda de `import` |
| Ningún adaptador contiene reglas del negocio | Revisión |
| Solo la raíz de composición crea adaptadores | Búsqueda de `new` |
| Cada excepción del núcleo tiene su traducción | La tabla y las pruebas |
| Cambiar el formato de entrada no tocó el núcleo | `git diff` |

**Entrega:** rama `taller/hexagonal-06`, con el proyecto en `taller/solucion/` y `ANALISIS.md` con la tabla del paso 2 y las respuestas de los pasos 5 y 8.

## En el trabajo real

En Spring, el adaptador HTTP es un `@RestController` que recibe los puertos de entrada por constructor, y la traducción de errores vive en un `@RestControllerAdvice`. La raíz de composición es el contenedor: o bien anotas los adaptadores y dejas que los encuentre, o bien declaras el servicio de aplicación con `@Bean` en una clase de configuración y mantienes el núcleo sin una sola anotación de Spring. La segunda opción es la que respeta la regla de dependencias al pie de la letra; la sesión 07 la usa.

Un consumidor de Kafka es otro adaptador de entrada: recibe un mensaje, lo traduce y llama al mismo puerto que el controlador. Está en la ruta de [Kafka y sagas](../../kafka-y-sagas/).

## Preguntas senior

1. ¿Qué diferencia hay entre un adaptador de entrada y uno de salida respecto al puerto?
2. ¿Qué validaciones hace un adaptador de entrada y cuáles no?
3. ¿Qué es la raíz de composición y por qué hay una sola?
4. ¿Por qué una orden repetida responde con éxito?
5. ¿Qué ganas al declarar el servicio con `@Bean` en lugar de anotarlo con `@Service`?
6. ¿Cómo sabes que una regla se coló en un adaptador?
