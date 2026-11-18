# 04 — WebFlux: rutas, WebClient, errores y flujos

## Idea

Un mostrador de atención con dos formas de organizarse. En una, cada empleado tiene su ventanilla con un letrero ("cuentas", "riesgos") y sabe qué hacer con lo que le llega: son los **controladores anotados**. En la otra, a la entrada hay una persona con una lista ("si pregunta por cuentas, a esta función; si algo sale mal de este tipo, responda esto"): son las **rutas funcionales**. Las dos atienden igual de rápido. Lo que decide si el mostrador funciona es otra cosa: que nadie se quede al teléfono esperando a otro departamento con la fila parada detrás.

## Lo esencial

### Qué es WebFlux

El marco web reactivo de Spring. Corre sobre Netty (por defecto) y sus manejadores devuelven `Mono` o `Flux`. WebFlux se suscribe a lo que devuelves y escribe la respuesta cuando llega.

Con Spring Boot 4 el iniciador es `spring-boot-starter-webflux`. El cliente `WebClient` y su `WebClient.Builder` configurado vienen en `spring-boot-starter-webclient`.

### Dos estilos, el mismo motor

| | Anotado | Funcional |
|---|---|---|
| Se escribe | `@RestController`, `@GetMapping`, como en MVC | `RouterFunction` que asocia rutas a funciones `ServerRequest -> Mono<ServerResponse>` |
| Errores | `@RestControllerAdvice` con `@ExceptionHandler` | `onError(...)` en la ruta, o filtros |
| A favor | Familiar; migrar desde MVC es casi copiar | Las rutas son datos: se componen, se prueban sin servidor, no hay reflexión |
| En contra | Mucha magia de anotaciones | Más código para lo simple |

Ninguno es "más reactivo". Elige uno por servicio y sé consistente. El ejemplo usa el anotado en la versión "antes" y el funcional en la corregida para que veas los dos, pero **los errores que corrige no tienen nada que ver con el estilo**: la versión corregida podría haberse escrito anotada.

### WebClient

El cliente HTTP no bloqueante. Es a `RestClient` lo que R2DBC es a JDBC.

```java
cliente.get().uri("/riesgo/{id}", cuentaId)
        .retrieve()                        // las respuestas 4xx y 5xx llegan como error
        .bodyToMono(Riesgo.class)
        .timeout(Duration.ofSeconds(1));   // nunca esperes para siempre a otro servicio
```

Mientras la respuesta no llega, ningún hilo espera. `RestClient` y `RestTemplate` dentro de un manejador de WebFlux bloquean un hilo del bucle durante toda la llamada.

### Errores

Tres decisiones para cada error:

| Error | Respuesta | Por qué |
|---|---|---|
| La cuenta no existe | 404 | Es culpa de quien pregunta, y puede corregirla |
| El servicio de riesgo no respondió a tiempo o falló | 503 | No es culpa de quien pregunta; puede reintentar más tarde |
| Un error inesperado | 500, sin detalles internos | Es nuestro |

El formato de la respuesta, `ProblemDetail` (RFC 9457), funciona igual en WebFlux que en MVC. Lo que no se debe hacer es lanzar `RuntimeException` genéricas: todas acaban en 500, y el cliente no distingue "no existe" de "estamos caídos".

### Flujos al cliente: Server-Sent Events

Si el manejador devuelve un `Flux` con tipo `text/event-stream`, WebFlux envía cada elemento **cuando existe**, como un evento `data:` en una conexión que se mantiene abierta. El cliente ve el primer dato enseguida, no al final. Sirve para extractos largos, avances de un proceso o notificaciones. Si devuelves `Mono<List<T>>` (o un `Flux` reunido con `collectList`), no hay flujo: hay una espera y luego todo de golpe.

## Ejemplo

Tres programas en `ejemplo/`:

| Carpeta | Qué es | Puerto |
|---|---|---|
| `servicio-de-riesgo/` | El "otro servicio". Solo JDK: `java ServicioDeRiesgo.java [puerto] [latencia]` | 8099 |
| `antes/` | WebFlux con un cliente bloqueante y errores genéricos | 8090 |
| `despues/` | WebFlux con WebClient, rutas funcionales, errores con nombre y SSE | 8091 |

Las dos aplicaciones fijan **cuatro hilos de bucle** (`reactor.netty.ioWorkerCount=4`) para que la medición dé lo mismo en cualquier máquina. Sin eso, Netty usa uno por núcleo.

<!-- file: ejemplo/servicio-de-riesgo/ServicioDeRiesgo.java -->
```java
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * El "otro servicio": evalúa el riesgo de una cuenta y tarda en responder. Solo usa el JDK.
 * Uso: java ServicioDeRiesgo.java [puerto] [latencia en ms]      (por defecto, 8099 y 300)
 */
public class ServicioDeRiesgo {

    public static void main(String[] args) throws IOException {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 8099;
        long latenciaMs = args.length > 1 ? Long.parseLong(args[1]) : 300;

        HttpServer servidor = HttpServer.create(new InetSocketAddress(puerto), 200);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());   // aguanta cientos de peticiones a la vez
        servidor.createContext("/riesgo/", intercambio -> {
            String cuenta = intercambio.getRequestURI().getPath().substring("/riesgo/".length());
            try {
                Thread.sleep(latenciaMs);
            } catch (InterruptedException interrupcion) {
                Thread.currentThread().interrupt();
            }
            byte[] cuerpo = ("{\"cuenta\":\"" + cuenta + "\",\"nivel\":\"BAJO\"}").getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidor.start();
        System.out.printf("Servicio de riesgo en el puerto %d, responde en %d ms%n", puerto, latenciaMs);
    }
}
```

Las dos aplicaciones comparten el almacén en memoria (la base reactiva llega en la sesión 05):

<!-- file: ejemplo/antes/src/main/java/banco/web/AlmacenDeCuentas.java -->
```java
package banco.web;

import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Cuentas en memoria. La base de datos reactiva llega en la sesión 05; aquí el tema es la capa web. */
@Component
public class AlmacenDeCuentas {

    private final Map<String, Cuenta> cuentas = Map.of(
            "cuenta-1", new Cuenta("cuenta-1", "Ana", 100_000_000),
            "cuenta-2", new Cuenta("cuenta-2", "Luis", 20_000_000));

    public Mono<Cuenta> buscar(String id) {
        return Mono.justOrEmpty(cuentas.get(id));
    }

    /** Un extracto largo que llega renglón a renglón, uno cada 100 ms, como lo entregaría una consulta grande. */
    public Flux<Movimiento> extracto(String cuentaId) {
        return Flux.interval(Duration.ofMillis(100))
                .take(10)
                .map(i -> new Movimiento(cuentaId, i.intValue() + 1, 1_000_00L * (i + 1), "movimiento " + (i + 1)));
    }
}
```

### Antes

<!-- file: ejemplo/antes/src/main/java/banco/web/ControladorDeCuentas.java -->
```java
package banco.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Mono;

/** Un controlador anotado que devuelve Mono en todas partes. Parece reactivo; por dentro, no lo es. */
@RestController
public class ControladorDeCuentas {

    private final AlmacenDeCuentas almacen;
    private final RestClient riesgo;

    public ControladorDeCuentas(AlmacenDeCuentas almacen, @Value("${riesgo.url}") String urlDeRiesgo) {
        this.almacen = almacen;
        this.riesgo = RestClient.builder()                       // el cliente HTTP "de siempre": bloqueante
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl(urlDeRiesgo)
                .build();
    }

    @GetMapping("/cuentas/{id}")
    public Mono<Cuenta> consultar(@PathVariable String id) {
        return almacen.buscar(id)
                .switchIfEmpty(Mono.error(new RuntimeException("no existe la cuenta " + id)));
    }

    @GetMapping("/cuentas/{id}/riesgo")
    public Mono<Riesgo> riesgo(@PathVariable String id) {
        Riesgo respuesta = riesgo.get().uri("/riesgo/{id}", id)  // el hilo del bucle espera aquí 300 ms
                .retrieve()
                .body(Riesgo.class);
        return Mono.just(respuesta);
    }

    @GetMapping("/cuentas/{id}/extracto")
    public Mono<List<Movimiento>> extracto(@PathVariable String id) {
        return almacen.extracto(id).collectList();              // nadie ve nada hasta tener el último renglón
    }
}
```

### Después

<!-- file: ejemplo/despues/src/main/java/banco/web/Rutas.java -->
```java
package banco.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** Las rutas, como datos: qué petición va a qué función, y cómo se traduce cada error. */
@Configuration
public class Rutas {

    @Bean
    public RouterFunction<ServerResponse> rutasDeCuentas(ManejadorDeCuentas manejador) {
        return RouterFunctions.route()
                .GET("/cuentas/{id}", manejador::consultar)
                .GET("/cuentas/{id}/riesgo", manejador::riesgo)
                .GET("/cuentas/{id}/extracto", manejador::extracto)
                .onError(CuentaNoEncontrada.class, (error, peticion) -> problema(HttpStatus.NOT_FOUND, error))
                .onError(RiesgoNoDisponible.class, (error, peticion) -> problema(HttpStatus.SERVICE_UNAVAILABLE, error))
                .build();
    }

    private static Mono<ServerResponse> problema(HttpStatus estado, Throwable error) {
        return ServerResponse.status(estado)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(ProblemDetail.forStatusAndDetail(estado, error.getMessage()));
    }
}
```

<!-- file: ejemplo/despues/src/main/java/banco/web/ManejadorDeCuentas.java -->
```java
package banco.web;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** Las funciones que atienden cada ruta. Reciben la petición y devuelven la respuesta "por llegar". */
@Component
public class ManejadorDeCuentas {

    private final AlmacenDeCuentas almacen;
    private final ClienteDeRiesgo riesgo;

    public ManejadorDeCuentas(AlmacenDeCuentas almacen, ClienteDeRiesgo riesgo) {
        this.almacen = almacen;
        this.riesgo = riesgo;
    }

    public Mono<ServerResponse> consultar(ServerRequest peticion) {
        return exigir(peticion.pathVariable("id"))
                .flatMap(cuenta -> ServerResponse.ok().bodyValue(cuenta));
    }

    public Mono<ServerResponse> riesgo(ServerRequest peticion) {
        return exigir(peticion.pathVariable("id"))
                .flatMap(cuenta -> riesgo.evaluar(cuenta.id()))   // el hilo queda libre mientras el otro responde
                .flatMap(evaluacion -> ServerResponse.ok().bodyValue(evaluacion));
    }

    /** Server-Sent Events: cada renglón sale hacia el cliente en cuanto existe. */
    public Mono<ServerResponse> extracto(ServerRequest peticion) {
        String id = peticion.pathVariable("id");
        return exigir(id).flatMap(cuenta -> ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(almacen.extracto(id), Movimiento.class));
    }

    private Mono<Cuenta> exigir(String id) {
        return almacen.buscar(id).switchIfEmpty(Mono.error(new CuentaNoEncontrada(id)));
    }
}
```

<!-- file: ejemplo/despues/src/main/java/banco/web/ClienteDeRiesgo.java -->
```java
package banco.web;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** El cliente del servicio de riesgo, con WebClient: pide, deja el hilo libre y sigue cuando llega la respuesta. */
@Component
public class ClienteDeRiesgo {

    static final Duration ESPERA_MAXIMA = Duration.ofSeconds(1);

    private final WebClient cliente;

    public ClienteDeRiesgo(WebClient.Builder constructor, @Value("${riesgo.url}") String urlDeRiesgo) {
        this.cliente = constructor.baseUrl(urlDeRiesgo).build();
    }

    public Mono<Riesgo> evaluar(String cuentaId) {
        return cliente.get().uri("/riesgo/{id}", cuentaId)
                .retrieve()                                       // 4xx y 5xx llegan como error
                .bodyToMono(Riesgo.class)
                .timeout(ESPERA_MAXIMA)                           // nunca esperar para siempre a otro
                .onErrorMap(RiesgoNoDisponible::new);             // un error técnico de fuera, con nombre propio
    }
}
```

<!-- file: ejemplo/despues/pom.xml -->
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.2</version>
        <relativePath/>
    </parent>

    <groupId>curso.webflux</groupId>
    <artifactId>s04-despues</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webclient</artifactId>
        </dependency>    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Ejecutarlo y medir

En tres terminales:

```bash
cd ejemplo/servicio-de-riesgo && java ServicioDeRiesgo.java
cd ejemplo/antes && mvn -q spring-boot:run
cd ejemplo/despues && mvn -q spring-boot:run
```

**Carga sobre el riesgo.** Veinte peticiones a la vez, con bash (`time` es la palabra reservada de bash; en zsh el formato cambia):

```bash
time (seq 20 | xargs -P 20 -I{} curl -s -o /dev/null localhost:8090/cuentas/cuenta-1/riesgo)
time (seq 20 | xargs -P 20 -I{} curl -s -o /dev/null localhost:8091/cuentas/cuenta-1/riesgo)
```

Y, mientras dura esa carga, una consulta que no llama al servicio de riesgo:

```bash
(seq 20 | xargs -P 20 -I{} curl -s -o /dev/null localhost:8090/cuentas/cuenta-1/riesgo) &
sleep 0.1
curl -s -o /dev/null -w 'consulta de cuenta-2 durante la carga: %{time_total} s\n' localhost:8090/cuentas/cuenta-2
wait
```

| | Antes (8090) | Después (8091) |
|---|---|---|
| 20 consultas de riesgo a la vez (300 ms cada una) | 1,55 s | 0,33 s |
| Consulta de `cuenta-2` durante esa carga | 1,44 s | 0,0015 s |

**El extracto.** Diez renglones que tardan 100 ms cada uno:

```bash
curl -s -o /dev/null -w 'primer byte %{time_starttransfer} s, total %{time_total} s\n' localhost:8090/cuentas/cuenta-1/extracto
curl -s -N -o /dev/null -w 'primer byte %{time_starttransfer} s, total %{time_total} s\n' localhost:8091/cuentas/cuenta-1/extracto
curl -s -N localhost:8091/cuentas/cuenta-1/extracto      # para verlo llegar renglón a renglón
```

```
antes:   primer byte 1.009826 s, total 1.009902 s
después: primer byte 0.112169 s, total 1.006446 s

data:{"cuentaId":"cuenta-1","renglon":1,"centavos":100000,"concepto":"movimiento 1"}

data:{"cuentaId":"cuenta-1","renglon":2,"centavos":200000,"concepto":"movimiento 2"}
...
```

**Los errores.** Una cuenta que no existe, el servicio de riesgo detenido (Ctrl+C en su terminal) y el servicio de riesgo lento (`java ServicioDeRiesgo.java 8099 3000`):

```bash
curl -s -w ' [%{http_code}]\n' localhost:8090/cuentas/cuenta-9
curl -s -w ' [%{http_code}]\n' localhost:8091/cuentas/cuenta-9
```

| Caso | Antes (8090) | Después (8091) |
|---|---|---|
| `cuenta-9` no existe | `500 Internal Server Error`, sin explicación | `404` `{"detail":"no existe la cuenta cuenta-9","status":404,"title":"Not Found"}` |
| Riesgo detenido | `500 Internal Server Error` | `503` `{"detail":"el servicio de riesgo no está disponible","status":503,...}` |
| Riesgo tarda 3 s | `200` en 3,03 s | `503` en 1,10 s |

### Análisis

| Lo que se ve | Por qué |
|---|---|
| 20 consultas tardan 1,55 s en "antes" | Cuatro hilos de bucle, cada uno dormido 300 ms en `RestClient` por cada consulta: cinco tandas. En "después", las 20 esperas ocurren a la vez sin ocupar hilos |
| Una consulta en memoria tarda 1,44 s durante la carga | Le tocó un hilo del bucle bloqueado en el servicio de riesgo. Es la sesión 03 en un servidor de verdad |
| El extracto se ve de golpe al segundo | `collectList()` espera al último renglón. Con SSE, el primero sale a los 112 ms |
| Todo error es 500 | `RuntimeException` no dice nada. El cliente no sabe si reintentar, corregir o desistir |
| Un servicio lento hace lento al nuestro | Sin tiempo límite, esperamos lo que el otro quiera. Con `timeout`, fallamos rápido y con una respuesta clara |

## Errores comunes

- `RestTemplate` o `RestClient` dentro de un manejador de WebFlux.
- `webClient...block()` dentro de un manejador: Reactor lo rechaza en un hilo de Netty (`block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-nio-N`).
- Crear un `WebClient` nuevo en cada petición, en lugar de uno por servicio remoto construido con el `WebClient.Builder` de Spring.
- `WebClient` sin `timeout`. Por defecto no hay tiempo límite de respuesta.
- `bodyToMono(String.class)` y luego convertir a mano.
- `collectList()` para devolver algo que podría ir en flujo, o lo contrario: SSE para tres elementos que caben en una respuesta normal.
- Tratar todos los errores del otro servicio igual: un 404 del otro servicio puede ser un 404 tuyo; un 500 suyo no es un 500 tuyo.
- En macOS verás al arrancar, como registro `ERROR` (aunque la aplicación funciona), `Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider`. Es solo un aviso: Netty usa la configuración DNS del sistema. Se quita añadiendo `io.netty:netty-resolver-dns-native-macos` con el clasificador de tu procesador, solo en desarrollo.

## Taller

**Objetivo:** exponer tu servicio de pedidos con WebFlux, consultando dos servicios externos sin bloquear, con errores con nombre y un flujo de eventos.

Sobre tu caso de uso "confirmar pedido" de las sesiones 02 y 03. Reutiliza `servicio-de-riesgo` como modelo para dos servicios simulados: inventario (puerto 8097) y pagos (8098), con latencia configurable.

1. Escribe un adaptador de entrada con rutas funcionales: `POST /pedidos/{id}/confirmacion`, `GET /pedidos/{id}` y `GET /pedidos/{id}/eventos` (SSE).
2. Los clientes de inventario y pagos con `WebClient`, cada uno con su tiempo límite justificado por escrito.
3. Errores: pedido inexistente (404), sin existencias (409), pago rechazado (422), un servicio externo caído o lento (503). Todos con `ProblemDetail`. Ninguna excepción genérica.
4. El endpoint SSE emite un evento por cada cambio de estado del pedido (reservado, cobrado, confirmado o fallido) mientras ocurre la confirmación. Pista: `Sinks.many().multicast()` por pedido.
5. Fija cuatro hilos de bucle. Con 40 confirmaciones simultáneas y los servicios externos en 300 ms, mide el tiempo total y la latencia de un `GET /pedidos/{id}` lanzado durante la carga.
6. Cambia el cliente de pagos por `RestClient` a propósito y repite el paso 5. Anota la diferencia y vuelve a dejar `WebClient`.
7. Para el servicio de pagos a mitad de la carga. ¿Qué responde tu servicio y en cuánto tiempo?

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Ninguna llamada externa bloquea el bucle | Paso 5 frente al 6: la consulta durante la carga responde en milisegundos |
| Cada error responde su código y un `ProblemDetail` | `curl` de cada caso, en `ANALISIS.md` |
| Ningún servicio externo puede colgar una petición más que su tiempo límite | Paso 7 |
| El flujo SSE muestra los estados en orden y mientras ocurren | `curl -N` durante una confirmación |

**Entrega:** rama `taller/webflux-04`, con el código en `taller/solucion/` y `ANALISIS.md` con las mediciones de los pasos 5, 6 y 7.

## En el trabajo real

Un servicio WebFlux típico es una fachada: recibe una petición, consulta tres o cuatro servicios (a veces en paralelo con `Mono.zip`), combina y responde. Ahí brilla, y ahí se rompe si un solo cliente bloquea. Lo primero que se mira en una revisión es la lista de clientes HTTP y de bases de datos: si alguno es síncrono, todo lo demás da igual. Lo segundo, los tiempos límite y qué se responde cuando vencen; junto con reintentos acotados y cortocircuitos (Resilience4j tiene operadores para Reactor), es lo que evita que un servicio lento arrastre a los demás. SSE se usa para paneles y avances; para comunicación en las dos direcciones, WebSocket, que WebFlux también soporta.

## Preguntas senior

1. ¿Qué diferencia hay entre controladores anotados y rutas funcionales? ¿Cuál elegirías para un servicio nuevo y por qué?
2. ¿Por qué `RestClient` dentro de WebFlux degrada a todo el servicio y no solo al endpoint que lo usa?
3. ¿Qué código HTTP devuelves cuando falla un servicio del que dependes? ¿Y cuando ese servicio te responde 404?
4. ¿Por qué todo `WebClient` necesita un tiempo límite, y cómo eliges su valor?
5. ¿Cuándo usarías SSE y cuándo una respuesta normal con una lista?
6. ¿Qué verías en las métricas de un servicio WebFlux con un cliente bloqueante escondido?
