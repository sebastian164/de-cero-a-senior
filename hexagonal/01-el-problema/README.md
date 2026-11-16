# 01 — El problema: cuando todo depende de todo

## Idea

Un computador portátil. Tiene puertos: USB, HDMI, audio. A un mismo puerto USB le conectas hoy un teclado, mañana una memoria y pasado una impresora, y el portátil no cambia por dentro. No sabe qué marca es el teclado; solo sabe qué forma tiene el enchufe y qué señales pasan por él. Ahora imagina un portátil con el teclado soldado a la placa y la impresora soldada a la pantalla. Funciona. Hasta el día que quieres cambiar una sola cosa.

La arquitectura hexagonal, que también se llama **de puertos y adaptadores**, propone lo primero: un núcleo que solo conoce la forma de sus enchufes, y adaptadores que se conectan a ellos.

## Lo esencial

| Pieza | Qué es | Qué conoce |
|---|---|---|
| **Dominio** | Las reglas del negocio: qué es una cuenta, cuándo se puede transferir | Nada más que a sí mismo |
| **Aplicación** | Los casos de uso: "abrir una cuenta", "transferir dinero". Coordina al dominio | El dominio y los puertos |
| **Puerto** | Una interfaz que define una conversación con el exterior | Tipos del núcleo |
| **Adaptador** | La pieza que traduce entre un puerto y una tecnología: HTTP, una consola, una base de datos, un broker | El puerto y su tecnología |

### La regla

**Las dependencias apuntan hacia dentro.** Un adaptador conoce a la aplicación; la aplicación no conoce a ningún adaptador. El dominio no conoce a nadie.

```
   adaptadores de entrada              adaptadores de salida
   (HTTP, consola, mensajes)           (base de datos, broker, otro servicio)
              │                                   ▲
              ▼                                   │
        puertos de entrada                 puertos de salida
              │                                   ▲
              └──────────►  APLICACIÓN  ──────────┘
                                │
                                ▼
                             DOMINIO
```

Hay dos tipos de puerto, y se distinguen por **quién llama a quién**:

| Puerto | Quién lo llama | Quién lo implementa | Ejemplo |
|---|---|---|---|
| De entrada | Un adaptador | La aplicación | `TransferirDinero` |
| De salida | La aplicación | Un adaptador | `RepositorioDeCuentas` |

### Qué se gana

- Las reglas del negocio se prueban en milisegundos, sin servidor ni base de datos.
- Cambiar de tecnología es escribir un adaptador, no tocar las reglas.
- El mismo caso de uso sirve a varias entradas: HTTP, una consola, un mensaje.
- El lugar donde empieza y termina una **transacción** queda a la vista: es el caso de uso.

Lo que cuesta, y cuándo no vale la pena, es la sesión 08. No es gratis.

## Ejemplo

Un banco pequeño: cuentas y transferencias entre ellas. Es el dominio del módulo entero, porque obliga a hablar de lo que más duele cuando sale mal: que el dinero se mueva completo o no se mueva.

### Antes

Ejecútalo con `java Main.java`.

<!-- file: ejemplo/antes/Main.java -->
```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Transferencias entre cuentas, todo en un método. Funciona. */
public class Main {

    static Map<String, Long> saldos = new HashMap<>();
    static Map<String, Long> transferidoHoy = new HashMap<>();
    static LocalDate dia = LocalDate.now();

    public static void main(String[] args) throws IOException {
        saldos.put("ana", 100_000_000L);                    // un millón de pesos, en centavos
        saldos.put("luis", 20_000_000L);
        String[] ordenes = {
            "transferir ana luis 30000000",
            "transferir luis ana 90000000",
            "transferir ana ana 100000",
            "transferir ana luis 60000000",
            "transferir ana pedro 100000",
            "saldo ana",
            "saldo luis",
        };
        for (String orden : ordenes) {
            procesar(orden);
        }
    }

    /** Lectura de la orden, reglas, saldos, registro y respuesta: en ese orden y sin separación. */
    static void procesar(String orden) throws IOException {
        String[] p = orden.split(" ");
        if (p[0].equals("saldo")) {
            System.out.println("SALDO " + p[1] + " = " + saldos.get(p[1]));
            return;
        }
        if (!LocalDate.now().equals(dia)) {
            dia = LocalDate.now();
            transferidoHoy.clear();
        }
        String origen = p[1];
        String destino = p[2];
        long valor = Long.parseLong(p[3]);
        if (origen.equals(destino)) {
            System.out.println("ERROR misma cuenta");
            return;
        }
        if (!saldos.containsKey(origen) || !saldos.containsKey(destino)) {
            System.out.println("ERROR cuenta inexistente");
            return;
        }
        if (saldos.get(origen) < valor) {
            System.out.println("ERROR saldo insuficiente");
            return;
        }
        if (transferidoHoy.getOrDefault(origen, 0L) + valor > 80_000_000L) {
            System.out.println("ERROR supera el tope diario");
            return;
        }
        saldos.put(origen, saldos.get(origen) - valor);                      // 1. sale el dinero
        Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "transferencias.log"),
                LocalDate.now() + " " + origen + " -> " + destino + " " + valor + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);      // 2. se registra (puede fallar)
        saldos.put(destino, saldos.get(destino) + valor);                    // 3. entra el dinero
        transferidoHoy.merge(origen, valor, Long::sum);
        System.out.println("OK " + origen + " -> " + destino + " " + valor);
    }
}
```

### Análisis

El código funciona y es corto. Ahora hazle estas preguntas:

| Quiero… | Qué me lo impide |
|---|---|
| Probar que no se puede superar el tope diario | La regla está dentro de un método que lee texto, escribe un archivo e imprime |
| Probar qué pasa al cambiar de día | `LocalDate.now()` está escrito ahí dentro. Habría que esperar a mañana |
| Recibir transferencias por HTTP además de por texto | Habría que copiar el método y cambiarle el principio: dos copias de las reglas |
| Guardar los saldos en una base de datos | Hay que editar el mismo método que contiene las reglas |
| Saber cuáles son las reglas del banco | Hay que leer todo y separar mentalmente lo que es regla de lo que es mecánica |
| **Que el dinero no desaparezca** | Si el paso 2 falla (disco lleno), el dinero ya salió del origen y nunca llega al destino. No hay nada que lo deshaga |
| Que dos transferencias simultáneas no se pisen | Dos hilos leen el mismo saldo, cada uno resta lo suyo y el último que escribe gana |
| Que reenviar la misma orden no transfiera dos veces | Nada reconoce una orden repetida |

Los tres últimos no son problemas de diseño estéticos: son dinero que aparece o desaparece. Para resolverlos hacen falta **transacciones**, y para ponerlas en el sitio correcto hace falta saber dónde empieza y termina una operación de negocio. Ese sitio es el caso de uso, y en este código no existe como pieza separada.

### Después

El "después" no cabe en un archivo: es un proyecto con paquetes. Está en [`../proyecto/`](../proyecto/) y lo vas a recorrer en las sesiones siguientes:

| Sesión | Qué se construye | Dónde queda |
|---|---|---|
| [02](../02-el-dominio/) | Las reglas, solas | `dominio/` |
| [03](../03-puertos-y-casos-de-uso/) | Los casos de uso y los puertos | `aplicacion/` |
| [04](../04-transacciones/) | Que el dinero se mueva completo o no se mueva | `aplicacion/` y un adaptador |
| [05](../05-concurrencia-y-aislamiento/) | Que dos operaciones a la vez no se pisen | Un adaptador y una regla de reintento |
| [06](../06-adaptadores-y-composicion/) | Consola, HTTP y el punto donde todo se conecta | `adaptadores/` y `Main` |
| [07](../07-persistencia-con-orm/) | La base de datos, con JPA | Otro adaptador |
| [08](../08-pruebas-y-limites/) | Pruebas por capa, la regla de dependencias y cuándo no | `pruebas/` |

## Errores comunes

- Creer que "hexagonal" es una estructura de carpetas. Es una regla sobre quién depende de quién.
- Empezar por los adaptadores o por el marco de trabajo. Se empieza por el dominio.
- Pensar que hace falta para cualquier programa. Para un guion de cincuenta líneas, el "antes" está bien.
- Confundirla con las capas tradicionales: en capas, la lógica depende de la persistencia; aquí, la persistencia depende de la lógica.

## Taller

**Objetivo:** sentir el problema en un código acoplado antes de aprender la solución.

A lo largo del módulo, el ejemplo es el banco y **tu proyecto es otro**: el servicio de pedidos de una tienda. En cada sesión le aplicarás a los pedidos lo que el ejemplo aplicó a las cuentas. Ese servicio será después la base de los [talleres de Kafka y sagas](../../kafka-y-sagas/) y de los de [HikariCP](../../hikaricp/).

Código de inicio: [`taller/inicio/Pedidos.java`](taller/inicio/Pedidos.java). Recibe un pedido por HTTP, lo valida, calcula el total, lo guarda y avisa al cliente. Al ejecutarlo con `java Pedidos.java` arranca un servidor en un puerto libre, se envía tres peticiones y termina.

<!-- file: taller/inicio/Pedidos.java -->
```java
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** El servicio de pedidos de una tienda. Lleva tiempo en producción. No tiene pruebas. */
public class Pedidos {

    static final Map<String, Long> PRECIOS = Map.of("teclado", 15_000_000L, "raton", 6_000_000L, "monitor", 90_000_000L);
    static final Map<String, Integer> EXISTENCIAS = new HashMap<>(Map.of("teclado", 10, "raton", 3, "monitor", 2));
    static int secuencia = 0;

    public static void main(String[] args) throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/pedidos", Pedidos::crearPedido);
        servidor.start();
        int puerto = servidor.getAddress().getPort();
        try {
            System.out.println(enviar(puerto, "cliente=cliente-1&linea=teclado:2&linea=raton:1"));
            System.out.println(enviar(puerto, "cliente=cliente-2&linea=raton:5"));
            System.out.println(enviar(puerto, "cliente=&linea=raton:1"));
        } finally {
            servidor.stop(0);
        }
    }

    static void crearPedido(HttpExchange peticion) throws IOException {
        String cuerpo = new String(peticion.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String cliente = "";
        long total = 0;
        StringBuilder lineas = new StringBuilder();
        int cantidadDeLineas = 0;
        for (String par : cuerpo.split("&")) {
            String[] claveValor = par.split("=", 2);
            if (claveValor[0].equals("cliente")) {
                cliente = claveValor.length > 1 ? claveValor[1] : "";
            }
            if (claveValor[0].equals("linea")) {
                String[] partes = claveValor[1].split(":");
                Long precio = PRECIOS.get(partes[0]);
                if (precio == null) {
                    responder(peticion, 422, "{\"error\":\"producto desconocido\"}");
                    return;
                }
                int cantidad = Integer.parseInt(partes[1]);
                if (cantidad < 1) {
                    responder(peticion, 422, "{\"error\":\"cantidad inválida\"}");
                    return;
                }
                if (EXISTENCIAS.get(partes[0]) < cantidad) {
                    responder(peticion, 422, "{\"error\":\"sin existencias\"}");
                    return;
                }
                EXISTENCIAS.merge(partes[0], -cantidad, Integer::sum);
                total += precio * cantidad;
                lineas.append(partes[0]).append(":").append(cantidad).append(":").append(precio).append(",");
                cantidadDeLineas++;
            }
        }
        if (cliente.isBlank()) {
            responder(peticion, 422, "{\"error\":\"falta el cliente\"}");
            return;
        }
        if (cantidadDeLineas == 0) {
            responder(peticion, 422, "{\"error\":\"pedido sin líneas\"}");
            return;
        }
        if (total > 500_000_000L) {
            responder(peticion, 422, "{\"error\":\"supera el tope\"}");
            return;
        }
        String id = "pedido-" + (++secuencia);
        Path archivo = Path.of(System.getProperty("java.io.tmpdir"), "pedidos.txt");
        Files.writeString(archivo, id + "|" + cliente + "|PENDIENTE|" + LocalDateTime.now() + "|" + lineas + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("  [correo a " + cliente + "] recibimos tu pedido " + id);
        responder(peticion, 201, "{\"id\":\"" + id + "\",\"totalCentavos\":" + total + "}");
    }

    static void responder(HttpExchange peticion, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        peticion.sendResponseHeaders(estado, bytes.length);
        peticion.getResponseBody().write(bytes);
        peticion.close();
    }

    static String enviar(int puerto, String cuerpo) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/pedidos"))
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
        HttpResponse<String> respuesta = HttpClient.newHttpClient().send(peticion, HttpResponse.BodyHandlers.ofString());
        return respuesta.statusCode() + " " + respuesta.body();
    }
}
```

1. Ejecútalo y explica cada línea de la salida.
2. Lista las reglas de negocio que contiene y en qué línea está cada una.
3. Lista todo lo que **no** es regla de negocio: de dónde llega la petición, dónde se guarda, de dónde salen los precios y las existencias, de dónde sale la fecha, cómo se avisa, cómo se responde.
4. Intenta escribir una prueba para "un pedido por encima del tope se rechaza" sin modificar el archivo. Anota cada obstáculo.
5. **Busca el dinero que desaparece.** Envía un pedido con dos líneas en el que la segunda sea inválida. Mira las existencias después. ¿Qué pasó con las de la primera línea? Explica el defecto con tus palabras.
6. Imagina dos peticiones simultáneas por el último monitor. Recorre el código a mano con las dos intercaladas. ¿Qué puede pasar?
7. Para cada uno de estos cambios, marca qué líneas habría que tocar: crear pedidos también desde un mensaje; guardar en una base de datos; avisar por otro canal; cambiar el tope.
8. Dibuja, en papel, cómo separarías este código en núcleo y exterior. No programes todavía. Guarda el dibujo.

**Criterios de aceptación**

- La lista de reglas y la lista de "lo demás" están completas y separadas.
- El defecto del paso 5 está reproducido, con la salida que lo demuestra.
- El dibujo del paso 8 existe.

**Entrega:** rama `taller/hexagonal-01`, con `taller/solucion/ANALISIS.md` y el dibujo.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Este "antes" no es una caricatura: es el aspecto que tiene un controlador de Spring con la lógica dentro, o un servicio que recibe la petición, arma la consulta SQL y envía el correo en el mismo método. Empieza siendo cómodo y se vuelve caro justo cuando el producto tiene éxito y hay que cambiarlo a menudo.

## Preguntas senior

1. ¿Qué problema resuelve la arquitectura hexagonal, dicho sin usar la palabra "capas"?
2. ¿Qué diferencia a un puerto de entrada de uno de salida?
3. ¿Hacia dónde apuntan las dependencias y por qué eso es lo importante?
4. ¿En qué se diferencia de una arquitectura en capas tradicional?
5. ¿Por qué el caso de uso es el lugar natural del límite de una transacción?
