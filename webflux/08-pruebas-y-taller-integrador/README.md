# 08 — Pruebas reactivas y taller integrador

## Idea

Un inspector que revisa un ascensor así: aprieta el botón del piso diez, se va a tomar un café y, al volver, ve que el ascensor está en el piso diez. "Funciona". No sabe si subió directo o pasó por el sótano, si tardó cinco segundos o cinco minutos, ni si al apretar el botón lo subió otra persona. Y si su informe dice "el ascensor estaba en el piso diez" pero lo escribió antes de que llegara, nadie se entera.

Las pruebas reactivas mal hechas son ese inspector: se suscriben, esperan "un rato" y miran el resultado, o escriben la afirmación donde nadie va a leerla.

## Lo esencial

### Las tres herramientas

| Herramienta | Qué prueba | Velocidad |
|---|---|---|
| **StepVerifier** (`reactor-test`) | Un `Mono` o `Flux`: qué valores emite, en qué orden, cómo termina, cuándo. Con tiempo virtual, los tiempos límite y los retrasos | Milisegundos |
| **WebTestClient** (`spring-boot-starter-webflux-test`) | La aplicación por HTTP: rutas, códigos, cuerpos JSON, errores | Segundos (arranca Spring) |
| **Testcontainers** | Contra una base real y desechable, creada para la ejecución y destruida al terminar | Segundos (arranca un contenedor) |

Y **BlockHound** encima de las pruebas de integración: si algo bloquea un hilo del bucle, la prueba falla (sesión 03).

### La pirámide, en reactivo

| Nivel | Qué | Con qué |
|---|---|---|
| Dominio | Las reglas | JUnit normal: el dominio no es reactivo (sesión 07) |
| Aplicación | Los casos de uso, con dobles de los puertos | StepVerifier |
| Adaptadores y extremo a extremo | SQL real, transacciones, HTTP, traducción de errores | WebTestClient + Testcontainers + BlockHound |
| Arquitectura | Que el dominio no importe Reactor ni Spring | Una prueba que lea los `import` |

### Reglas

1. **Nunca afirmes dentro de `subscribe()`.** La afirmación corre en otro hilo, quizá cuando la prueba ya terminó; si falla, Reactor la registra como error no atendido y la prueba pasa.
2. **Nunca esperes con `Thread.sleep`.** Si el resultado llega antes, perdiste tiempo; si llega después, la prueba falla al azar. StepVerifier espera exactamente lo necesario.
3. **`.block()` en una prueba está permitido** para preparar datos. No es el bucle de eventos.
4. **Cada prueba con sus datos.** Claves e identificadores únicos (`UUID.randomUUID()`), nada que dependa de lo que dejó la ejecución anterior.
5. **Base real y desechable.** Una base levantada a mano acumula estado entre ejecuciones; una en memoria distinta de PostgreSQL no prueba ni el SQL ni los bloqueos.

## Ejemplo

Se prueba el banco reactivo de la [sesión 07](../07-el-banco-hexagonal-reactivo/). Los dos proyectos de esta sesión no tienen código de producción propio: su `pom.xml` compila el núcleo del módulo hexagonal y la versión corregida de la sesión 07 desde donde están, y solo añaden pruebas.

### Antes

Necesita el [entorno](../entorno/) levantado a mano: las pruebas usan la base `banco` del puerto 5433.

<!-- file: ejemplo/antes/src/test/java/banco/PruebasDelBancoTest.java -->
```java
package banco;

import static org.junit.jupiter.api.Assertions.assertEquals;

import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.reactiva.ServicioDeCuentas;
import banco.aplicacion.reactiva.puertos.entrada.TransferirDinero.Orden;
import banco.arranque.AplicacionBanco;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Tres pruebas que pasan. Ninguna demuestra lo que dice su nombre. */
@SpringBootTest(classes = AplicacionBanco.class)
class PruebasDelBancoTest {

    @Autowired
    ServicioDeCuentas servicio;

    @Test
    void abrirUnaCuenta() {
        VistaDeCuenta ana = servicio.abrir("Ana", 100_000).block();

        assertEquals(100_000, ana.saldoCentavos());
    }

    @Test
    void consultarUnaCuenta() {
        VistaDeCuenta ana = servicio.abrir("Ana", 100_000).block();

        servicio.porId(ana.id())
                .subscribe(vista -> assertEquals(999, vista.saldoCentavos()));   // 999: está mal a propósito
    }

    @Test
    void transferir() throws InterruptedException {
        VistaDeCuenta ana = servicio.abrir("Ana", 100_000).block();
        VistaDeCuenta luis = servicio.abrir("Luis", 0).block();

        servicio.transferir(new Orden("orden-1", ana.id(), luis.id(), 30_000)).subscribe();
        Thread.sleep(2_000);                                       // "para que le dé tiempo"

        assertEquals(70_000, servicio.porId(ana.id()).block().saldoCentavos());
    }
}
```

```bash
cd ejemplo/antes
mvn test        # una vez
mvn test        # y otra
```

Primera ejecución:

```
ERROR --- [ctor-tcp-nio-11] reactor.core.publisher.Operators : Operator called default onErrorDropped
reactor.core.Exceptions$ErrorCallbackNotImplemented: org.opentest4j.AssertionFailedError: expected: <999> but was: <100000>
...
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Segunda ejecución, sin tocar nada:

```
[ERROR] banco.PruebasDelBancoTest.transferir -- Time elapsed: 2.019 s <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: <70000> but was: <100000>
[ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0
[ERROR] BUILD FAILURE
```

### Análisis

| Lo que se ve | Por qué |
|---|---|
| `consultarUnaCuenta` pasa aunque espera 999 | La afirmación corre en el hilo de R2DBC (`reactor-tcp-nio-11`). Falla allí, Reactor no tiene a quién entregar el error y lo registra (`onErrorDropped`). JUnit ya había dado la prueba por buena. El error está en el registro, a la vista de nadie |
| `transferir` tarda dos segundos | `Thread.sleep(2_000)` "para que le dé tiempo". La transferencia tarda milisegundos |
| `transferir` pasa la primera vez y falla la segunda | Usa la clave fija `orden-1`. La base del entorno conserva la orden de la ejecución anterior, así que la segunda vez la transferencia es `REPETIDA` y el saldo no cambia. La prueba depende de algo que no controla |
| Hace falta levantar el entorno antes | Sin él, las tres fallan al arrancar Spring. En integración continua, alguien tiene que acordarse |

Si la afirmación del 999 se escribe con StepVerifier, la prueba falla como debe:

```
java.lang.AssertionError: expectation "expectNextMatches" failed (predicate failed on value: VistaDeCuenta[id=cuenta-1, titular=Ana, saldoCentavos=100000])
```

### Después

Tres clases de prueba. No necesita el entorno: Testcontainers levanta un PostgreSQL 18.7 para la ejecución y lo destruye al terminar. Sí necesita Docker.

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
    <artifactId>s08-despues</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>21</java.version>
        <start-class>banco.arranque.AplicacionBanco</start-class>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-r2dbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>r2dbc-postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <!-- WebTestClient -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux-test</artifactId>
            <scope>test</scope>
        </dependency>
        <!-- StepVerifier -->
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
        <!-- Un PostgreSQL de verdad, desechable, por cada ejecución de las pruebas -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-r2dbc</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor.tools</groupId>
            <artifactId>blockhound</artifactId>
            <version>1.0.18.RELEASE</version>              <!-- no la fija Spring Boot -->
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Aquí no hay código de producción propio: se prueba el banco reactivo de la sesión 07, compilado desde
                 donde está (su núcleo, del módulo hexagonal; sus adaptadores y su configuración, de la sesión 07). -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>build-helper-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>nucleo</id>
                        <phase>generate-sources</phase>
                        <goals>
                            <goal>add-source</goal>
                        </goals>
                        <configuration>
                            <sources>
                                <source>../../../../hexagonal/proyecto/src</source>
                                <source>../../../07-el-banco-hexagonal-reactivo/ejemplo/despues/src/main/java</source>
                            </sources>
                        </configuration>
                    </execution>
                    <execution>
                        <id>recursos-del-banco</id>
                        <phase>generate-resources</phase>
                        <goals>
                            <goal>add-resource</goal>
                        </goals>
                        <configuration>
                            <resources>
                                <resource>
                                    <directory>../../../07-el-banco-hexagonal-reactivo/ejemplo/despues/src/main/resources</directory>
                                </resource>
                            </resources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <!-- Entra el dominio, sin cambios. De la aplicación entran las piezas que no hablan con el exterior
                 (vistas, errores, eventos y el Reloj); el servicio y los puertos que hacen E/S se reescriben en
                 banco.aplicacion.reactiva. Los adaptadores del núcleo y su Main se quedan fuera. -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>banco/adaptadores/salida/memoria/**</exclude>
                        <exclude>banco/adaptadores/salida/sistema/**</exclude>
                        <exclude>banco/adaptadores/entrada/consola/**</exclude>
                        <exclude>banco/adaptadores/entrada/http/**</exclude>
                        <exclude>banco/Main.java</exclude>
                        <exclude>banco/aplicacion/ServicioDeCuentas.java</exclude>
                        <exclude>banco/aplicacion/puertos/entrada/**</exclude>
                        <exclude>banco/aplicacion/puertos/salida/OrdenesProcesadas.java</exclude>
                        <exclude>banco/aplicacion/puertos/salida/PublicadorDeEventos.java</exclude>
                        <exclude>banco/aplicacion/puertos/salida/RegistroDeMovimientos.java</exclude>
                        <exclude>banco/aplicacion/puertos/salida/RepositorioDeCuentas.java</exclude>
                        <exclude>banco/aplicacion/puertos/salida/UnidadDeTrabajo.java</exclude>
                    </excludes>
                </configuration>
            </plugin>
            <!-- BlockHound necesita estas opciones de la JVM desde el JDK 13 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <argLine>-XX:+AllowRedefinitionToAddDeleteMethods -XX:+EnableDynamicAgentLoading</argLine>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**La aplicación, con StepVerifier y sin Spring.** Los puertos se sustituyen por un almacén en memoria que respeta lo que prometen: versión, unicidad de órdenes y todo o nada.

<!-- file: ejemplo/despues/src/test/java/banco/apoyo/AlmacenDePrueba.java -->
```java
package banco.apoyo;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.reactiva.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.reactiva.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.reactiva.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.reactiva.puertos.salida.RepositorioDeCuentas;
import banco.aplicacion.reactiva.puertos.salida.UnidadDeTrabajo;
import banco.dominio.Cuenta;
import banco.dominio.Movimiento;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Los puertos de salida en memoria, para probar el servicio sin base de datos. Respeta lo que prometen los puertos:
 * versión (conflictos), unicidad de órdenes y todo o nada (si el trabajo falla, se restaura la foto anterior).
 * Puede fingir conflictos: los próximos "conflictosPorFingir" guardados fallan como si otro hubiera escrito antes.
 */
public final class AlmacenDePrueba implements RepositorioDeCuentas, RegistroDeMovimientos, OrdenesProcesadas,
        PublicadorDeEventos, UnidadDeTrabajo {

    private Map<String, Cuenta> cuentas = new HashMap<>();
    private List<Movimiento> movimientos = new ArrayList<>();
    private Set<String> ordenes = new HashSet<>();
    private List<EventoDeCuenta> eventos = new ArrayList<>();
    private int siguiente = 1;

    public final AtomicInteger transacciones = new AtomicInteger();
    public int conflictosPorFingir = 0;

    // ---- UnidadDeTrabajo ----

    @Override
    public <T> Mono<T> ejecutar(Supplier<Mono<T>> trabajo) {
        return Mono.defer(() -> {
            transacciones.incrementAndGet();
            Map<String, Cuenta> fotoCuentas = new HashMap<>(cuentas);
            List<Movimiento> fotoMovimientos = new ArrayList<>(movimientos);
            Set<String> fotoOrdenes = new HashSet<>(ordenes);
            List<EventoDeCuenta> fotoEventos = new ArrayList<>(eventos);
            return trabajo.get().doOnError(error -> {             // revertir: volver a la foto
                cuentas = fotoCuentas;
                movimientos = fotoMovimientos;
                ordenes = fotoOrdenes;
                eventos = fotoEventos;
            });
        });
    }

    // ---- RepositorioDeCuentas ----

    @Override
    public String siguienteId() {
        return "cuenta-" + siguiente++;
    }

    @Override
    public Mono<Void> guardar(Cuenta cuenta) {
        return Mono.fromRunnable(() -> {
            Cuenta guardada = cuentas.get(cuenta.id());
            long versionGuardada = guardada == null ? 0 : guardada.version();
            if (conflictosPorFingir > 0 || versionGuardada != cuenta.version()) {
                if (conflictosPorFingir > 0) {
                    conflictosPorFingir--;
                }
                throw new ConflictoDeConcurrencia("la cuenta " + cuenta.id());
            }
            cuentas.put(cuenta.id(), copia(cuenta, cuenta.version() + 1));
        });
    }

    @Override
    public Mono<Cuenta> buscar(String id) {
        return Mono.fromSupplier(() -> cuentas.get(id)).map(guardada -> copia(guardada, guardada.version()));
    }

    private static Cuenta copia(Cuenta cuenta, long version) {   // quien lee recibe una copia, como de una base real
        return Cuenta.existente(cuenta.id(), cuenta.titular(), cuenta.saldo(), cuenta.enviadoHoy(), cuenta.diaDelTope(), version);
    }

    // ---- RegistroDeMovimientos ----

    @Override
    public Mono<Void> registrar(Movimiento movimiento) {
        return Mono.fromRunnable(() -> movimientos.add(movimiento));
    }

    @Override
    public Flux<Movimiento> de(String cuentaId) {
        return Flux.defer(() -> Flux.fromIterable(List.copyOf(movimientos)))
                .filter(movimiento -> movimiento.cuentaId().equals(cuentaId));
    }

    // ---- OrdenesProcesadas ----

    @Override
    public Mono<Boolean> registrarSiEsNueva(String clave) {
        return Mono.fromSupplier(() -> ordenes.add(clave));
    }

    // ---- PublicadorDeEventos ----

    @Override
    public Mono<Void> publicar(EventoDeCuenta evento) {
        return Mono.fromRunnable(() -> eventos.add(evento));
    }

    public int cuantosMovimientos() {
        return movimientos.size();
    }

    public int cuantosEventos() {
        return eventos.size();
    }
}
```

<!-- file: ejemplo/despues/src/test/java/banco/ServicioDeCuentasTest.java -->
```java
package banco;

import static org.assertj.core.api.Assertions.assertThat;

import banco.apoyo.AlmacenDePrueba;
import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.reactiva.ServicioDeCuentas;
import banco.aplicacion.reactiva.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.reactiva.puertos.entrada.TransferirDinero.Resultado;
import banco.dominio.ReglaDeNegocioViolada;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** El servicio reactivo, sin Spring ni base de datos. Cada prueba tarda milisegundos y no depende de las demás. */
class ServicioDeCuentasTest {

    private AlmacenDePrueba almacen;
    private ServicioDeCuentas servicio;
    private String ana;
    private String luis;

    @BeforeEach
    void prepararDosCuentas() {
        almacen = new AlmacenDePrueba();
        servicio = new ServicioDeCuentas(almacen, almacen, almacen, almacen, () -> Instant.parse("2026-11-15T12:00:00Z"), almacen);
        ana = servicio.abrir("Ana", 100_000).map(VistaDeCuenta::id).block();     // block() en una prueba sí está bien
        luis = servicio.abrir("Luis", 0).map(VistaDeCuenta::id).block();
        almacen.transacciones.set(0);
    }

    @Test
    void sinSuscripcionNoOcurreNada() {
        servicio.transferir(new Orden("orden-1", ana, luis, 30_000));       // ensamblada y descartada

        assertThat(almacen.transacciones).hasValue(0);
        StepVerifier.create(servicio.porId(ana)).expectNextMatches(vista -> vista.saldoCentavos() == 100_000).verifyComplete();
    }

    @Test
    void transfiereUnaVezAunqueLaOrdenLlegueDosVeces() {
        Orden orden = new Orden("orden-1", ana, luis, 30_000);

        StepVerifier.create(servicio.transferir(orden)).expectNext(Resultado.REALIZADA).verifyComplete();
        StepVerifier.create(servicio.transferir(orden)).expectNext(Resultado.REPETIDA).verifyComplete();

        StepVerifier.create(servicio.porId(ana).map(VistaDeCuenta::saldoCentavos)).expectNext(70_000L).verifyComplete();
        StepVerifier.create(servicio.porId(luis).map(VistaDeCuenta::saldoCentavos)).expectNext(30_000L).verifyComplete();
        assertThat(almacen.cuantosEventos()).isEqualTo(1);
    }

    @Test
    void sinSaldoEsUnErrorDeNegocioYNoDejaRastro() {
        int movimientosAntes = almacen.cuantosMovimientos();

        StepVerifier.create(servicio.transferir(new Orden("orden-1", ana, luis, 999_999)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ReglaDeNegocioViolada.class)
                        .hasMessageContaining("saldo insuficiente"))
                .verify();

        assertThat(almacen.cuantosMovimientos()).isEqualTo(movimientosAntes);
        // La orden tampoco quedó registrada: se puede volver a intentar con saldo
        StepVerifier.create(servicio.transferir(new Orden("orden-1", ana, luis, 1_000))).expectNext(Resultado.REALIZADA).verifyComplete();
    }

    @Test
    void unConflictoSeReintentaDesdeElPrincipio() {
        almacen.conflictosPorFingir = 1;

        StepVerifier.create(servicio.transferir(new Orden("orden-1", ana, luis, 30_000)))
                .expectNext(Resultado.REALIZADA)                   // y no REPETIDA: el primer intento se revirtió entero
                .verifyComplete();

        assertThat(almacen.transacciones).hasValue(2);
    }

    @Test
    void tresConflictosSeguidosLleganAQuienLlama() {
        almacen.conflictosPorFingir = 3;

        StepVerifier.create(servicio.transferir(new Orden("orden-1", ana, luis, 30_000)))
                .expectError(ConflictoDeConcurrencia.class)
                .verify();

        assertThat(almacen.transacciones).hasValue(3);
    }
}
```

**El banco completo por HTTP**, con WebTestClient, PostgreSQL en Testcontainers y BlockHound:

<!-- file: ejemplo/despues/src/test/java/banco/BancoHttpTest.java -->
```java
package banco;

import static org.assertj.core.api.Assertions.assertThat;

import banco.aplicacion.VistaDeCuenta;
import banco.arranque.AplicacionBanco;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.blockhound.BlockHound;

/**
 * El banco completo: HTTP real, WebFlux, R2DBC y un PostgreSQL desechable que Testcontainers levanta y destruye.
 * Con BlockHound instalado, cualquier llamada bloqueante en un hilo del bucle de eventos rompe la prueba.
 */
@SpringBootTest(classes = AplicacionBanco.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class BancoHttpTest {

    @Container
    @ServiceConnection                                             // Spring Boot toma de aquí la URL de R2DBC
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7");

    @Autowired
    WebTestClient http;

    @BeforeAll
    static void instalarBlockHound() {
        BlockHound.builder()
                // UUID.randomUUID() lee /dev/urandom: técnicamente es E/S de archivo, en la práctica no espera nunca.
                // Se permite de forma explícita y solo ahí; cualquier otra llamada bloqueante sigue rompiendo la prueba.
                .allowBlockingCallsInside("java.util.UUID", "randomUUID")
                .install();
    }

    @Test
    void transfiereYNoRepiteLaMismaOrden() {
        String ana = abrir("Ana", 100_000);
        String luis = abrir("Luis", 0);
        String clave = UUID.randomUUID().toString();               // cada prueba con sus datos: no depende de las demás

        transferir(clave, ana, luis, 30_000).expectStatus().isCreated()
                .expectBody().jsonPath("$.resultado").isEqualTo("REALIZADA");
        transferir(clave, ana, luis, 30_000).expectStatus().isOk()
                .expectBody().jsonPath("$.resultado").isEqualTo("REPETIDA");

        assertThat(saldo(ana)).isEqualTo(70_000);
        assertThat(saldo(luis)).isEqualTo(30_000);
    }

    @Test
    void unaTransferenciaAUnaCuentaQueNoExisteNoDejaRastro() {
        String ana = abrir("Ana", 100_000);

        transferir(UUID.randomUUID().toString(), ana, "cuenta-que-no-existe", 30_000)
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("no existe la cuenta cuenta-que-no-existe");

        assertThat(saldo(ana)).isEqualTo(100_000);                 // la transacción se revirtió entera
        http.get().uri("/cuentas/{id}/movimientos", ana).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(1);   // solo el de apertura
    }

    @Test
    void sinSaldoResponde422() {
        String ana = abrir("Ana", 1_000);
        String luis = abrir("Luis", 0);

        transferir(UUID.randomUUID().toString(), ana, luis, 5_000)
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.detail").isEqualTo("saldo insuficiente en " + ana);
    }

    @Test
    void unaCuentaQueNoExisteResponde404() {
        http.get().uri("/cuentas/{id}", "cuenta-que-no-existe").exchange()
                .expectStatus().isNotFound();
    }

    // ---- ayudas ----

    private String abrir(String titular, long centavos) {
        return http.post().uri("/cuentas")
                .bodyValue(Map.of("titular", titular, "centavos", centavos))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(VistaDeCuenta.class)
                .returnResult().getResponseBody().id();
    }

    private WebTestClient.ResponseSpec transferir(String clave, String origen, String destino, long centavos) {
        return http.post().uri("/transferencias")
                .header("Idempotency-Key", clave)
                .bodyValue(Map.of("origen", origen, "destino", destino, "centavos", centavos))
                .exchange();
    }

    private long saldo(String cuentaId) {
        return http.get().uri("/cuentas/{id}", cuentaId).exchange()
                .expectStatus().isOk()
                .expectBody(VistaDeCuenta.class)
                .returnResult().getResponseBody().saldoCentavos();
    }
}
```

**La arquitectura**, como prueba:

<!-- file: ejemplo/despues/src/test/java/banco/ArquitecturaTest.java -->
```java
package banco;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Las decisiones de la sesión 07, comprobadas: si alguien las rompe sin querer, la construcción falla. */
class ArquitecturaTest {

    static final Path DOMINIO = Path.of("../../../../hexagonal/proyecto/src/banco/dominio");
    static final Path APLICACION_REACTIVA = Path.of("../../../07-el-banco-hexagonal-reactivo/ejemplo/despues/src/main/java/banco/aplicacion/reactiva");

    @Test
    void elDominioNoConoceAReactorNiASpring() throws IOException {
        assertThat(importsDe(DOMINIO)).isNotEmpty()
                .noneMatch(linea -> linea.contains("reactor.") || linea.contains("org.springframework."));
    }

    @Test
    void laAplicacionReactivaConoceAReactorPeroNoASpring() throws IOException {
        List<String> imports = importsDe(APLICACION_REACTIVA);
        assertThat(imports).anyMatch(linea -> linea.contains("reactor.core.publisher"));
        assertThat(imports).noneMatch(linea -> linea.contains("org.springframework."));
    }

    private static List<String> importsDe(Path carpeta) throws IOException {
        try (Stream<Path> archivos = Files.walk(carpeta)) {
            return archivos.filter(archivo -> archivo.toString().endsWith(".java"))
                    .flatMap(archivo -> {
                        try {
                            return Files.readAllLines(archivo).stream();
                        } catch (IOException error) {
                            throw new IllegalStateException(error);
                        }
                    })
                    .filter(linea -> linea.startsWith("import "))
                    .toList();
        }
    }
}
```

```bash
cd ejemplo/despues
mvn test
```

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.038 s -- in banco.ArquitecturaTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.710 s -- in banco.BancoHttpTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s -- in banco.ServicioDeCuentasTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Se pueden ejecutar diez veces seguidas: cada una empieza con una base vacía y cada prueba crea sus datos.

### Lo que encontró BlockHound al preparar esta sesión

La primera versión de `BancoHttpTest` instalaba BlockHound sin excepciones, y la primera prueba falló con un 500:

```
reactor.blockhound.BlockingOperationError: Blocking call! java.io.FileInputStream#readBytes
	at java.base/java.io.FileInputStream.readBytes(FileInputStream.java)
	...
	at java.base/sun.security.provider.NativePRNG$RandomIO.readFully(NativePRNG.java:432)
	...
	at java.base/java.security.SecureRandom.nextBytes(SecureRandom.java:768)
	at java.base/java.util.UUID.randomUUID(UUID.java:153)
	at banco.adaptadores.salida.r2dbc.AlmacenR2dbc.siguienteId(AlmacenR2dbc.java:34)
	at banco.aplicacion.reactiva.ServicioDeCuentas.lambda$abrir$0(ServicioDeCuentas.java:57)
```

`UUID.randomUUID()` usa `SecureRandom`, que en macOS y Linux lee `/dev/urandom`: es una lectura de archivo, y se ejecutaba en un hilo del bucle al abrir una cuenta. BlockHound tenía razón: **técnicamente** es E/S bloqueante en el bucle. Y había que decidir:

| Opción | Valoración |
|---|---|
| Quitar BlockHound | No: perderías la detección de todo lo demás |
| Generar el identificador en `boundedElastic` | Un cambio de hilo en cada apertura para una lectura que no espera |
| Un generador sin `SecureRandom` | Identificadores de cuenta predecibles: un problema de seguridad |
| **Permitirlo, solo ahí, y escribir por qué** | `/dev/urandom` no se bloquea esperando entropía en un sistema arrancado: la lectura dura microsegundos |

Se eligió la última, con `allowBlockingCallsInside("java.util.UUID", "randomUUID")`. Es el uso correcto de BlockHound: una lista de excepciones **corta, explícita y justificada**, en lugar de desactivarlo la primera vez que molesta.

## Errores comunes

- `subscribe(v -> assertEquals(...))` en una prueba.
- `Thread.sleep` para esperar a una cadena reactiva.
- `StepVerifier.create(...).expectNext(...)` sin `verify()`, `verifyComplete()` o `verifyError()` al final: sin ese paso no se suscribe y no comprueba nada.
- Probar un tiempo límite de 30 s esperando 30 s, en lugar de `withVirtualTime`.
- Crear el `Mono` fuera del `Supplier` de `withVirtualTime`: los operadores de tiempo capturan el reloj al ensamblar y el tiempo virtual no les afecta.
- Pruebas que comparten datos fijos (`orden-1`, `cuenta-1`) contra una base que sobrevive entre ejecuciones.
- Una base en memoria (H2) para probar SQL de PostgreSQL: `on conflict do nothing` y los bloqueos no se comportan igual.
- Desactivar BlockHound cuando encuentra algo, en lugar de mirar qué encontró.
- No limpiar: Testcontainers lo hace con su contenedor Ryuk; si lo desactivas, los contenedores quedan vivos.

## Taller integrador

**Objetivo:** entregar tu servicio de pedidos reactivo completo, probado en todos los niveles, medido y defendido.

Junta lo que construiste en las sesiones 02 a 07: dominio sin cambios del módulo hexagonal, aplicación con puertos reactivos, adaptadores R2DBC (o MongoDB, si lo justificas), WebFlux con rutas funcionales, clientes `WebClient` para inventario y pagos, y un flujo SSE de estados.

1. **Pruebas de aplicación** con StepVerifier y dobles en memoria: caso feliz, pedido inexistente, sin existencias, pago rechazado, conflicto de concurrencia con reintento, tres conflictos seguidos y tiempo límite de pagos (con tiempo virtual).
2. **Pruebas extremo a extremo** con WebTestClient, Testcontainers y BlockHound. Los servicios de inventario y pagos se simulan con un servidor HTTP de prueba (MockWebServer de OkHttp o WireMock) o con el `ServicioDeRiesgo` de la sesión 04 adaptado. Cubre al menos: confirmar, confirmar dos veces la misma orden, un servicio externo caído (503) y un pedido que se revierte entero.
3. **Prueba de arquitectura**: el dominio no importa `reactor` ni `org.springframework`; la aplicación no importa `org.springframework`.
4. **BlockHound** con su lista de excepciones, cada una con una línea que la justifique.
5. **Medición**: con cuatro hilos de bucle, 200 confirmaciones concurrentes y los servicios externos en 300 ms. Latencia media y percentil 95 de las confirmaciones, y de un `GET /pedidos/{id}` lanzado durante la carga.
6. **Simulacro**: con la carga del paso 5 en marcha, bloquea durante diez segundos la fila de existencias de un producto desde `psql` (como `bloqueo.sh`). Describe qué peticiones se ven afectadas y cuáles no, y por qué.
7. **Defensa**: un `ANALISIS.md` que responda, con tus números: ¿valió la pena el modelo reactivo para este servicio frente a MVC con hilos virtuales? ¿Qué te costó? ¿Qué harías distinto si empezaras de cero?

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| `mvn test` pasa dos veces seguidas sin preparar nada a mano (salvo tener Docker) | Ejecución |
| Ninguna prueba usa `Thread.sleep` ni afirma dentro de `subscribe` | Búsqueda en el código |
| BlockHound está activo en las pruebas extremo a extremo y cada excepción está justificada | Código y comentarios |
| El dominio no cambió respecto al módulo hexagonal | `git diff` |
| Las mediciones de los pasos 5 y 6 están en `ANALISIS.md` | Revisión |
| La defensa del paso 7 cita mediciones, costos y alternativas | Revisión |

**Entrega:** rama `taller/webflux-08`, con el proyecto en `taller/solucion/` y `ANALISIS.md`. En la revisión se pedirá romper a propósito una línea de la aplicación (por ejemplo, cambiar un `flatMap` por un `doOnNext`) y ver qué prueba falla.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

En los equipos que trabajan con WebFlux, las pruebas extremo a extremo con WebTestClient y Testcontainers son la red de seguridad principal, y BlockHound va en ellas desde el primer día: un bloqueo encontrado en una prueba cuesta minutos; en producción, un incidente. Las pruebas con StepVerifier cubren la lógica de composición (qué se llama, en qué orden, qué pasa con cada error) y son las que se ejecutan en cada guardado. Lo que se revisa con lupa en un *pull request* es cualquier `subscribe()` o `Thread.sleep` en una prueba: casi siempre esconden una prueba que pasa sin probar nada.

## Preguntas senior

1. ¿Por qué una afirmación dentro de `subscribe()` puede no hacer fallar una prueba?
2. ¿Qué ventajas tiene StepVerifier sobre `.block()` para probar un `Flux`?
3. ¿Cómo funciona el tiempo virtual y qué error típico lo anula?
4. ¿Por qué Testcontainers y no una base en memoria para probar un adaptador R2DBC?
5. BlockHound marca una llamada en una biblioteca de terceros. ¿Cómo decides si la permites o la quitas del bucle?
6. ¿Qué pruebas del módulo hexagonal siguen sirviendo tras pasar a puertos reactivos, y cuáles hay que reescribir?
