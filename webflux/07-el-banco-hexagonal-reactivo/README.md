# 07 — El banco hexagonal reactivo

> Requiere el módulo de [arquitectura hexagonal](../../hexagonal/), sobre todo las sesiones de [puertos](../../hexagonal/03-puertos-y-casos-de-uso/), [transacciones](../../hexagonal/04-transacciones/) y [persistencia con ORM](../../hexagonal/07-persistencia-con-orm/). Los dos ejemplos compilan el núcleo del banco **desde donde está**, sin copiarlo ni modificarlo.

## Idea

Una casa diseñada para que cambiar los electrodomésticos sea fácil: todos se enchufan a la misma toma. Funciona mientras los electrodomésticos nuevos usen el mismo voltaje. Si un día quieres uno que funciona con otro voltaje, no basta con cambiar el aparato: hay que cambiar las tomas, y quizá parte del cableado. La casa sigue siendo buena; simplemente, el voltaje era parte del diseño de las tomas aunque nadie lo hubiera escrito.

En el banco, las tomas son los puertos. Su "voltaje" es que son **síncronos**: `Optional<Cuenta> buscar(String id)` promete devolver la cuenta *cuando el método retorne*. Un adaptador no bloqueante no puede cumplir esa promesa.

## Lo esencial

### Lo que dice la arquitectura hexagonal, y lo que no dice

La hexagonal promete que puedes cambiar la infraestructura sin tocar el dominio ni la aplicación. Lo prometido se cumplió en el módulo hexagonal: de la memoria a JPA no cambió una línea del núcleo. Pero esa promesa tiene una condición implícita: que el **modelo de ejecución** de la infraestructura nueva quepa en la forma de los puertos. JPA bloquea, como la memoria; cabe. R2DBC no bloquea; no cabe.

| Puerto síncrono | Lo que obliga a hacer a un adaptador R2DBC |
|---|---|
| `Optional<Cuenta> buscar(id)` | Esperar la consulta: `.block()`, es decir, bloquear |
| `<T> T ejecutar(Supplier<T>)` en la unidad de trabajo | Abrir la transacción en un hilo y asumir que todo el trabajo ocurre en él |

### Tres opciones

| | Opción | Qué cambia | Costo |
|---|---|---|---|
| A | **Núcleo síncrono, adaptadores bloqueantes, sin WebFlux**: MVC con hilos virtuales y JDBC o JPA | Nada del núcleo | Ninguno arquitectónico. Es lo que ya tenías, con otra configuración |
| B | **Núcleo síncrono dentro de WebFlux**, cada llamada envuelta en `fromCallable(...).subscribeOn(boundedElastic())` | Solo el adaptador de entrada | Un hilo ocupado por petición en curso, como en MVC, más la complejidad de WebFlux. Funciona, pero no aprovecha nada |
| C | **Puertos reactivos**: la aplicación devuelve `Mono` y `Flux` | La capa de aplicación (servicio y puertos que hacen E/S), todos los adaptadores, todas las pruebas de la aplicación | Ver la tabla del final |

Y una cuarta que no es opción: **núcleo síncrono llamado directamente desde WebFlux**, con adaptadores JDBC. Compila, funciona, pasa las pruebas... y bloquea el bucle de eventos en cada petición. Es el "antes" del ejemplo.

### ¿Mono en el dominio?

No. El dominio del banco (`Cuenta`, `Dinero`, `Transferencia`) es **cálculo puro sobre objetos en memoria**: no espera a nadie. Nada en él necesita `Mono`. Poner Reactor en el dominio lo acoplaría a una biblioteca de infraestructura sin ningún beneficio, y obligaría a escribir reglas de negocio como cadenas de operadores.

`Mono` y `Flux` aparecen donde hay **espera**: en los puertos que hacen E/S y en el servicio de aplicación que los coordina. Un puerto sin E/S, como `Reloj`, se queda síncrono. Esa es la regla: el tipo reactivo marca la frontera de la espera, no la arquitectura entera.

### Lo que cambia en la aplicación

| Concepto | Versión síncrona | Versión reactiva |
|---|---|---|
| "No existe" | `Optional.empty()` | `Mono` vacío; `switchIfEmpty(Mono.error(...))` |
| Secuencia | Una línea después de otra | `then`, `flatMap`, `zipWhen` |
| Transacción | `ejecutar(Supplier<T>)`: ThreadLocal | `ejecutar(Supplier<Mono<T>>)`: contexto de Reactor. Recibe un `Supplier` para que cada reintento construya el trabajo de nuevo |
| Reintento ante conflicto | Un bucle `for` con `try/catch` | `retryWhen(Retry.max(2).filter(...))`: volver a suscribirse |
| Regla de negocio violada | `throw` desde el dominio | El mismo `throw`, dentro de un `flatMap`: Reactor lo convierte en señal de error |

## Ejemplo

```
ejemplo/
  bloqueo.sh                      la medición: una fila bloqueada y una consulta ajena
  antes/                          puerto 8094: el núcleo intacto, adaptadores JDBC, WebFlux por fuera
    src/main/java/banco/
      adaptadores/salida/jdbc/      AlmacenJdbc, UnidadDeTrabajoJdbc
      adaptadores/entrada/webflux/  ControladorDeCuentas, TraductorDeErrores, Peticiones
      arranque/                     AplicacionBanco, Configuracion (la raíz de composición)
  despues/                        puerto 8095: dominio intacto, aplicación reactiva, adaptadores R2DBC
    src/main/java/banco/
      aplicacion/reactiva/          ServicioDeCuentas y sus puertos con Mono y Flux
      adaptadores/salida/r2dbc/     AlmacenR2dbc, UnidadDeTrabajoR2dbc
      adaptadores/entrada/webflux/  ControladorDeCuentas, TraductorDeErrores, Peticiones
      arranque/                     AplicacionBanco, Configuracion
```

Las dos usan la base `banco` del [entorno](../entorno/) con el esquema del módulo hexagonal (`schema.sql`, copiado de `hexagonal/proyecto-jpa`) y fijan cuatro hilos de bucle.

Los paquetes se llaman `adaptadores/salida` como en el núcleo. El `.gitignore` de la raíz del repositorio ignora `salida/` (la carpeta de compilación del banco); el [`.gitignore` de este módulo](../.gitignore) lo deshace para las carpetas de código.

### Antes: el núcleo síncrono dentro de WebFlux

El `pom.xml` compila `../../../../hexagonal/proyecto/src` sin sus adaptadores ni su `Main`, igual que `proyecto-jpa`:

<!-- file: ejemplo/antes/pom.xml -->
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
    <artifactId>s07-antes</artifactId>
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
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- El núcleo no se copia: se compila desde el módulo hexagonal, tal como está. -->
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
                            </sources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <!-- Entran el dominio y la aplicación, sin cambios. Los adaptadores del núcleo y su Main se quedan fuera. -->
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
                    </excludes>
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

<!-- file: ejemplo/antes/src/main/java/banco/adaptadores/salida/jdbc/UnidadDeTrabajoJdbc.java -->
```java
package banco.adaptadores.salida.jdbc;

import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** El puerto de transacciones sobre JDBC. La transacción queda atada al hilo que llama: así funciona JDBC. */
@Component
public class UnidadDeTrabajoJdbc implements UnidadDeTrabajo {

    private final TransactionTemplate transacciones;

    public UnidadDeTrabajoJdbc(PlatformTransactionManager gestor) {
        this.transacciones = new TransactionTemplate(gestor);
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return transacciones.execute(estado -> trabajo.get());   // si ya hay una abierta en este hilo, se une a ella
    }
}
```

<!-- file: ejemplo/antes/src/main/java/banco/adaptadores/salida/jdbc/AlmacenJdbc.java -->
```java
package banco.adaptadores.salida.jdbc;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Los puertos de salida del banco con SQL y JdbcTemplate. Usa la conexión de la transacción del hilo actual. */
@Repository
public class AlmacenJdbc implements RepositorioDeCuentas, RegistroDeMovimientos, OrdenesProcesadas, PublicadorDeEventos {

    private final JdbcTemplate jdbc;

    public AlmacenJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- RepositorioDeCuentas ----

    @Override
    public String siguienteId() {
        return "cuenta-" + UUID.randomUUID();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        if (cuenta.version() == 0) {
            jdbc.update("insert into cuenta (id, titular, saldo_centavos, enviado_hoy_centavos, dia_del_tope, version) "
                            + "values (?, ?, ?, ?, ?, 1)",
                    cuenta.id(), cuenta.titular(), cuenta.saldo().centavos(), cuenta.enviadoHoy().centavos(), cuenta.diaDelTope());
            return;
        }
        int filas = jdbc.update("update cuenta set saldo_centavos = ?, enviado_hoy_centavos = ?, dia_del_tope = ?, "
                        + "version = version + 1 where id = ? and version = ?",
                cuenta.saldo().centavos(), cuenta.enviadoHoy().centavos(), cuenta.diaDelTope(), cuenta.id(), cuenta.version());
        if (filas == 0) {
            throw new ConflictoDeConcurrencia("la cuenta " + cuenta.id());
        }
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        return jdbc.query("select titular, saldo_centavos, enviado_hoy_centavos, dia_del_tope, version from cuenta where id = ?",
                (fila, n) -> Cuenta.existente(id, fila.getString("titular"), new Dinero(fila.getLong("saldo_centavos")),
                        new Dinero(fila.getLong("enviado_hoy_centavos")), fila.getObject("dia_del_tope", LocalDate.class),
                        fila.getLong("version")), id).stream().findFirst();
    }

    // ---- RegistroDeMovimientos ----

    @Override
    public void registrar(Movimiento movimiento) {
        jdbc.update("insert into movimiento (cuenta_id, tipo, centavos, fecha, referencia) values (?, ?, ?, ?, ?)",
                movimiento.cuentaId(), movimiento.tipo().name(), movimiento.valor().centavos(),
                enUtc(movimiento.fecha()), movimiento.referencia());
    }

    @Override
    public List<Movimiento> de(String cuentaId) {
        return jdbc.query("select tipo, centavos, fecha, referencia from movimiento where cuenta_id = ? order by id",
                (fila, n) -> new Movimiento(cuentaId, Movimiento.Tipo.valueOf(fila.getString("tipo")),
                        new Dinero(fila.getLong("centavos")), fila.getObject("fecha", OffsetDateTime.class).toInstant(),
                        fila.getString("referencia")), cuentaId);
    }

    // ---- OrdenesProcesadas ----

    @Override
    public boolean registrarSiEsNueva(String clave) {
        return jdbc.update("insert into orden_procesada (clave) values (?) on conflict do nothing", clave) == 1;
    }

    // ---- PublicadorDeEventos: la bandeja de salida ----

    @Override
    public void publicar(EventoDeCuenta evento) {
        jdbc.update("insert into evento_pendiente (id, tipo, cuenta_id, centavos, ocurrido_en) values (?, ?, ?, ?, ?)",
                evento.id(), evento.tipo(), evento.cuentaId(), evento.centavos(), enUtc(evento.ocurridoEn()));
    }

    private static OffsetDateTime enUtc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
```

<!-- file: ejemplo/antes/src/main/java/banco/adaptadores/entrada/webflux/ControladorDeCuentas.java -->
```java
package banco.adaptadores.entrada.webflux;

import banco.adaptadores.entrada.webflux.Peticiones.Apertura;
import banco.adaptadores.entrada.webflux.Peticiones.Deposito;
import banco.adaptadores.entrada.webflux.Peticiones.Transferencia;
import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.entrada.AbrirCuenta;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.puertos.entrada.Depositar;
import banco.aplicacion.puertos.entrada.TransferirDinero;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.dominio.Movimiento;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Un adaptador WebFlux sobre los puertos síncronos del núcleo. Compila, responde bien y pasa cualquier prueba
 * funcional. Pero cada llamada al núcleo es JDBC, y se ejecuta en el hilo del bucle de eventos.
 */
@RestController
public class ControladorDeCuentas {

    private final AbrirCuenta abrirCuenta;
    private final Depositar depositar;
    private final TransferirDinero transferirDinero;
    private final ConsultarCuenta consultarCuenta;

    public ControladorDeCuentas(AbrirCuenta abrirCuenta, Depositar depositar, TransferirDinero transferirDinero,
                                ConsultarCuenta consultarCuenta) {
        this.abrirCuenta = abrirCuenta;
        this.depositar = depositar;
        this.transferirDinero = transferirDinero;
        this.consultarCuenta = consultarCuenta;
    }

    @PostMapping("/cuentas")
    public Mono<ResponseEntity<VistaDeCuenta>> abrir(@RequestBody Apertura apertura) {
        VistaDeCuenta cuenta = abrirCuenta.abrir(apertura.titular(), apertura.centavos());
        return Mono.just(ResponseEntity.status(HttpStatus.CREATED).body(cuenta));
    }

    @PostMapping("/cuentas/{id}/depositos")
    public Mono<VistaDeCuenta> depositar(@PathVariable String id, @RequestBody Deposito deposito) {
        return Mono.just(depositar.depositar(id, deposito.centavos()));
    }

    @PostMapping("/transferencias")
    public Mono<ResponseEntity<Map<String, String>>> transferir(@RequestHeader("Idempotency-Key") String clave,
                                                                @RequestBody Transferencia transferencia) {
        Resultado resultado = transferirDinero.transferir(
                new Orden(clave, transferencia.origen(), transferencia.destino(), transferencia.centavos()));
        HttpStatus estado = resultado == Resultado.REALIZADA ? HttpStatus.CREATED : HttpStatus.OK;
        return Mono.just(ResponseEntity.status(estado).body(Map.of("resultado", resultado.name())));
    }

    @GetMapping("/cuentas/{id}")
    public Mono<VistaDeCuenta> consultar(@PathVariable String id) {
        return Mono.justOrEmpty(consultarCuenta.porId(id))
                .switchIfEmpty(Mono.error(new CuentaNoEncontrada(id)));
    }

    @GetMapping("/cuentas/{id}/movimientos")
    public Flux<Movimiento> movimientos(@PathVariable String id) {
        return Flux.fromIterable(consultarCuenta.movimientosDe(id));
    }
}
```

<!-- file: ejemplo/antes/src/main/java/banco/arranque/Configuracion.java -->
```java
package banco.arranque;

import banco.adaptadores.salida.jdbc.AlmacenJdbc;
import banco.adaptadores.salida.jdbc.UnidadDeTrabajoJdbc;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.salida.Reloj;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** La raíz de composición. El servicio es el del módulo hexagonal, sin una línea cambiada. */
@Configuration
public class Configuracion {

    @Bean
    public Reloj reloj() {
        return Instant::now;
    }

    @Bean
    public ServicioDeCuentas servicioDeCuentas(AlmacenJdbc almacen, UnidadDeTrabajoJdbc unidadDeTrabajo, Reloj reloj) {
        return new ServicioDeCuentas(almacen, almacen, almacen, unidadDeTrabajo, reloj, almacen);
    }
}
```

### La medición

Las pruebas funcionales no ven nada raro: abrir, transferir, repetir la orden (`REPETIDA`), superar el tope diario (422), transferir a una cuenta que no existe (404). Las dos versiones responden **lo mismo**, salvo identificadores y fechas.

Para ver el problema hace falta una espera en la base. La más realista no es una consulta lenta artificial, sino un **bloqueo de fila**: otra sesión (un proceso por lotes, un informe con `select ... for update`) retiene la fila de Ana durante cinco segundos. Mientras tanto llegan cuatro transferencias desde Ana (una por hilo del bucle) y, medio segundo después, alguien consulta la cuenta de Eva, que nadie tiene bloqueada.

<!-- file: ejemplo/bloqueo.sh -->
```bash
#!/usr/bin/env bash
# Una fila bloqueada en PostgreSQL y cuatro transferencias que la esperan. ¿Qué le pasa a una consulta que no tiene nada que ver?
# Uso: ./bloqueo.sh 8094   (antes: JDBC)      ./bloqueo.sh 8095   (después: R2DBC)
# Necesita el entorno levantado (docker compose -p webflux up -d, desde ../../entorno).
set -euo pipefail
PUERTO=${1:-8094}
COMPOSE="docker compose -p webflux -f $(dirname "$0")/../../entorno/compose.yaml"

abrir() {
  curl -s -X POST "localhost:$PUERTO/cuentas" -H 'Content-Type: application/json' \
    -d "{\"titular\":\"$1\",\"centavos\":$2}" | sed -E 's/.*"id":"([^"]+)".*/\1/'
}
ANA=$(abrir Ana 100000000)
LUIS=$(abrir Luis 0)
EVA=$(abrir Eva 5000000)

# Otra sesión (un proceso por lotes, un informe con FOR UPDATE...) retiene la fila de Ana durante 5 segundos
$COMPOSE exec -T postgres psql -q -U banco -d banco \
  -c "begin; select id from cuenta where id = '$ANA' for update; select pg_sleep(5); commit;" > /dev/null &
until [ "$($COMPOSE exec -T postgres psql -tA -U banco -d banco -c \
  "select count(*) from pg_stat_activity where state = 'active' and query like '%pg_sleep(5)%' and pid <> pg_backend_pid()")" = "1" ]; do
  sleep 0.2                                                  # espera a que el bloqueo esté tomado de verdad
done

# Cuatro transferencias desde Ana, una por hilo del bucle de eventos: las cuatro esperan a ese bloqueo
for i in 1 2 3 4; do
  curl -s -o /dev/null -w "transferencia $i: %{http_code} en %{time_total} s\n" -X POST "localhost:$PUERTO/transferencias" \
    -H "Idempotency-Key: bloqueo-$PUERTO-$RANDOM-$i" -H 'Content-Type: application/json' \
    -d "{\"origen\":\"$ANA\",\"destino\":\"$LUIS\",\"centavos\":100000}" &
done
sleep 0.5

# Mientras tanto, alguien consulta la cuenta de Eva, que nadie tiene bloqueada
curl -s -o /dev/null -w "consulta de Eva:  %{http_code} en %{time_total} s\n" "localhost:$PUERTO/cuentas/$EVA"
wait
```

```bash
cd ejemplo/antes && mvn -q spring-boot:run       # en una terminal
cd ejemplo/despues && mvn -q spring-boot:run     # en otra, cuando la primera haya arrancado
./ejemplo/bloqueo.sh 8094
./ejemplo/bloqueo.sh 8095
```

```
== antes (8094)
transferencia 4: 201 en 4.991817 s
transferencia 2: 201 en 4.996757 s
transferencia 3: 201 en 5.005620 s
transferencia 1: 409 en 5.006806 s
consulta de Eva:  200 en 4.500881 s

== después (8095)
consulta de Eva:  200 en 0.003527 s
transferencia 4: 201 en 4.699335 s
transferencia 2: 201 en 4.707892 s
transferencia 1: 201 en 4.713603 s
transferencia 3: 409 en 4.713287 s
```

### Análisis

| Lo que se ve | Por qué |
|---|---|
| Las transferencias tardan casi cinco segundos en las dos versiones | Esperan al bloqueo de la fila, como deben. La base es la misma |
| La consulta de Eva tarda 4,5 s en "antes" y 3,5 ms en "después" | En "antes", cada transferencia ocupa un hilo del bucle **dormido en JDBC** dentro del `UPDATE`. Con los cuatro dormidos, la conexión de Eva cae en un hilo que no puede atenderla hasta que se libere el bloqueo. En "después" las transferencias esperan sin ocupar hilos |
| Una de las cuatro transferencias termina en 409 | Las cuatro leyeron la misma versión de la cuenta de Ana. Al liberarse el bloqueo, una gana; las demás encuentran otra versión, reintentan desde el principio y vuelven a competir. Tras tres intentos, una se rinde. Es el bloqueo optimista del módulo hexagonal, igual en las dos versiones |
| El núcleo no tiene ningún error | El problema no está en el dominio ni en la aplicación, sino en el **contrato** de los puertos frente al modelo de ejecución del adaptador de entrada |

Un bloqueo de fila de cinco segundos es raro. Una consulta de 300 ms en un pico de carga, una réplica lenta o una base saturada, no. El efecto es el mismo a menor escala, y en todas las rutas del servicio a la vez.

### Después: puertos reactivos

El `pom.xml` sigue compilando el núcleo, pero deja fuera el servicio y los puertos que hacen E/S: se reescriben en `banco.aplicacion.reactiva`. Entran sin cambios el dominio entero, `VistaDeCuenta`, `EventoDeCuenta`, las excepciones de la aplicación y el puerto `Reloj`.

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
    <artifactId>s07-despues</artifactId>
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
    </dependencies>

    <build>
        <plugins>
            <!-- El núcleo no se copia: se compila desde el módulo hexagonal, tal como está. -->
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
                            </sources>
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
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Los puertos de salida:

<!-- file: ejemplo/despues/src/main/java/banco/aplicacion/reactiva/puertos/salida/RepositorioDeCuentas.java -->
```java
package banco.aplicacion.reactiva.puertos.salida;

import banco.dominio.Cuenta;
import reactor.core.publisher.Mono;

public interface RepositorioDeCuentas {

    /** Sin E/S: sigue siendo síncrono. Un Mono aquí solo añadiría ruido. */
    String siguienteId();

    /** Termina con error ConflictoDeConcurrencia si otro cambió la cuenta desde que se leyó. */
    Mono<Void> guardar(Cuenta cuenta);

    Mono<Cuenta> buscar(String id);
}
```

<!-- file: ejemplo/despues/src/main/java/banco/aplicacion/reactiva/puertos/salida/UnidadDeTrabajo.java -->
```java
package banco.aplicacion.reactiva.puertos.salida;

import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/**
 * Todo lo que ocurra dentro se confirma junto, o no ocurre. Recibe un Supplier y no un Mono ya armado:
 * cada suscripción (cada reintento) debe construir el trabajo de nuevo, dentro de una transacción nueva.
 */
public interface UnidadDeTrabajo {

    <T> Mono<T> ejecutar(Supplier<Mono<T>> trabajo);
}
```

Los demás (`RegistroDeMovimientos`, `OrdenesProcesadas`, `PublicadorDeEventos`) y los de entrada siguen el mismo patrón: el mismo método, con `Mono` o `Flux` donde había un valor o una lista.

El servicio. Compáralo línea a línea con [`banco.aplicacion.ServicioDeCuentas`](../../hexagonal/proyecto/src/banco/aplicacion/ServicioDeCuentas.java):

<!-- file: ejemplo/despues/src/main/java/banco/aplicacion/reactiva/ServicioDeCuentas.java -->
```java
package banco.aplicacion.reactiva;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.reactiva.puertos.entrada.AbrirCuenta;
import banco.aplicacion.reactiva.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.reactiva.puertos.entrada.Depositar;
import banco.aplicacion.reactiva.puertos.entrada.TransferirDinero;
import banco.aplicacion.reactiva.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.reactiva.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.reactiva.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.reactiva.puertos.salida.RepositorioDeCuentas;
import banco.aplicacion.reactiva.puertos.salida.UnidadDeTrabajo;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import banco.dominio.Transferencia;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Los mismos casos de uso que banco.aplicacion.ServicioDeCuentas, con puertos que devuelven Mono y Flux.
 * Las reglas siguen en el dominio, que no cambió: Cuenta y Transferencia no saben que existe Reactor.
 */
public final class ServicioDeCuentas implements AbrirCuenta, Depositar, TransferirDinero, ConsultarCuenta {

    private static final int INTENTOS = 3;

    private final RepositorioDeCuentas cuentas;
    private final RegistroDeMovimientos movimientos;
    private final OrdenesProcesadas ordenes;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Reloj reloj;
    private final PublicadorDeEventos eventos;

    public ServicioDeCuentas(RepositorioDeCuentas cuentas, RegistroDeMovimientos movimientos, OrdenesProcesadas ordenes,
                             UnidadDeTrabajo unidadDeTrabajo, Reloj reloj, PublicadorDeEventos eventos) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.ordenes = ordenes;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.reloj = reloj;
        this.eventos = eventos;
    }

    @Override
    public Mono<VistaDeCuenta> abrir(String titular, long saldoInicialCentavos) {
        return unidadDeTrabajo.ejecutar(() -> {
            Instant ahora = reloj.ahora();
            Cuenta cuenta = Cuenta.abrir(cuentas.siguienteId(), titular, new Dinero(saldoInicialCentavos), dia(ahora));
            Mono<Void> deposito = saldoInicialCentavos > 0
                    ? movimientos.registrar(new Movimiento(cuenta.id(), Movimiento.Tipo.DEPOSITO, cuenta.saldo(), ahora, "apertura"))
                    : Mono.empty();
            return cuentas.guardar(cuenta)
                    .then(deposito)
                    .thenReturn(VistaDeCuenta.de(cuenta));
        });
    }

    @Override
    public Mono<VistaDeCuenta> depositar(String cuentaId, long centavos) {
        return conReintento(unidadDeTrabajo.ejecutar(() -> exigir(cuentaId).flatMap(cuenta -> {
            Instant ahora = reloj.ahora();
            Dinero valor = new Dinero(centavos);
            cuenta.depositar(valor);                                       // el dominio, igual que antes
            return cuentas.guardar(cuenta)
                    .then(movimientos.registrar(new Movimiento(cuentaId, Movimiento.Tipo.DEPOSITO, valor, ahora, "depósito")))
                    .thenReturn(VistaDeCuenta.de(cuenta));
        })));
    }

    @Override
    public Mono<Resultado> transferir(Orden orden) {
        return conReintento(unidadDeTrabajo.ejecutar(() -> ordenes.registrarSiEsNueva(orden.clave())
                .flatMap(esNueva -> esNueva ? realizar(orden) : Mono.just(Resultado.REPETIDA))));
    }

    private Mono<Resultado> realizar(Orden orden) {
        Instant ahora = reloj.ahora();
        Dinero valor = new Dinero(orden.centavos());
        return exigir(orden.origenId())
                .zipWhen(origen -> exigir(orden.destinoId()))             // una después de otra: una conexión, una consulta a la vez
                .flatMap(par -> {
                    Cuenta origen = par.getT1();
                    Cuenta destino = par.getT2();
                    Transferencia.ejecutar(origen, destino, valor, dia(ahora));   // si lanza, el flatMap lo vuelve señal de error
                    return cuentas.guardar(origen)
                            .then(cuentas.guardar(destino))
                            .then(movimientos.registrar(new Movimiento(origen.id(), Movimiento.Tipo.TRANSFERENCIA_ENVIADA, valor, ahora, orden.clave())))
                            .then(movimientos.registrar(new Movimiento(destino.id(), Movimiento.Tipo.TRANSFERENCIA_RECIBIDA, valor, ahora, orden.clave())))
                            .then(eventos.publicar(new EventoDeCuenta("transferencia:" + orden.clave(), "TransferenciaRealizada",
                                    origen.id(), orden.centavos(), ahora)))
                            .thenReturn(Resultado.REALIZADA);
                });
    }

    @Override
    public Mono<VistaDeCuenta> porId(String cuentaId) {
        return cuentas.buscar(cuentaId).map(VistaDeCuenta::de);
    }

    @Override
    public Flux<Movimiento> movimientosDe(String cuentaId) {
        return movimientos.de(cuentaId);
    }

    private Mono<Cuenta> exigir(String cuentaId) {
        return cuentas.buscar(cuentaId).switchIfEmpty(Mono.error(() -> new CuentaNoEncontrada(cuentaId)));
    }

    private static LocalDate dia(Instant instante) {
        return LocalDate.ofInstant(instante, ZoneOffset.UTC);
    }

    /** El bucle for de la versión síncrona, como operador: volver a suscribirse es volver a empezar la transacción. */
    private static <T> Mono<T> conReintento(Mono<T> operacion) {
        return operacion.retryWhen(Retry.max(INTENTOS - 1)
                .filter(ConflictoDeConcurrencia.class::isInstance)
                .onRetryExhaustedThrow((especificacion, senal) -> senal.failure()));
    }
}
```

Los adaptadores de salida. El SQL es el mismo que el de `AlmacenJdbc`:

<!-- file: ejemplo/despues/src/main/java/banco/adaptadores/salida/r2dbc/UnidadDeTrabajoR2dbc.java -->
```java
package banco.adaptadores.salida.r2dbc;

import banco.aplicacion.reactiva.puertos.salida.UnidadDeTrabajo;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * El puerto de transacciones sobre R2DBC. La transacción no vive en el hilo (el trabajo pasa por varios):
 * viaja en el contexto de la suscripción, y DatabaseClient la encuentra ahí.
 */
@Component
public class UnidadDeTrabajoR2dbc implements UnidadDeTrabajo {

    private final TransactionalOperator transacciones;

    public UnidadDeTrabajoR2dbc(TransactionalOperator transacciones) {
        this.transacciones = transacciones;
    }

    @Override
    public <T> Mono<T> ejecutar(Supplier<Mono<T>> trabajo) {
        return transacciones.transactional(Mono.defer(trabajo));     // si ya hay una en el contexto, se une a ella
    }
}
```

<!-- file: ejemplo/despues/src/main/java/banco/adaptadores/salida/r2dbc/AlmacenR2dbc.java -->
```java
package banco.adaptadores.salida.r2dbc;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.reactiva.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.reactiva.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.reactiva.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.reactiva.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Los puertos de salida con SQL y DatabaseClient. El mismo SQL que el adaptador JDBC; otra forma de esperarlo. */
@Repository
public class AlmacenR2dbc implements RepositorioDeCuentas, RegistroDeMovimientos, OrdenesProcesadas, PublicadorDeEventos {

    private final DatabaseClient db;

    public AlmacenR2dbc(DatabaseClient db) {
        this.db = db;
    }

    // ---- RepositorioDeCuentas ----

    @Override
    public String siguienteId() {
        return "cuenta-" + UUID.randomUUID();
    }

    @Override
    public Mono<Void> guardar(Cuenta cuenta) {
        if (cuenta.version() == 0) {
            return db.sql("insert into cuenta (id, titular, saldo_centavos, enviado_hoy_centavos, dia_del_tope, version) "
                            + "values (:id, :titular, :saldo, :enviado, :dia, 1)")
                    .bind("id", cuenta.id())
                    .bind("titular", cuenta.titular())
                    .bind("saldo", cuenta.saldo().centavos())
                    .bind("enviado", cuenta.enviadoHoy().centavos())
                    .bind("dia", cuenta.diaDelTope())
                    .then();
        }
        return db.sql("update cuenta set saldo_centavos = :saldo, enviado_hoy_centavos = :enviado, dia_del_tope = :dia, "
                        + "version = version + 1 where id = :id and version = :version")
                .bind("saldo", cuenta.saldo().centavos())
                .bind("enviado", cuenta.enviadoHoy().centavos())
                .bind("dia", cuenta.diaDelTope())
                .bind("id", cuenta.id())
                .bind("version", cuenta.version())
                .fetch().rowsUpdated()
                .flatMap(filas -> filas == 0
                        ? Mono.error(new ConflictoDeConcurrencia("la cuenta " + cuenta.id()))
                        : Mono.empty());
    }

    @Override
    public Mono<Cuenta> buscar(String id) {
        return db.sql("select titular, saldo_centavos, enviado_hoy_centavos, dia_del_tope, version from cuenta where id = :id")
                .bind("id", id)
                .map(fila -> Cuenta.existente(id, fila.get("titular", String.class),
                        new Dinero(fila.get("saldo_centavos", Long.class)), new Dinero(fila.get("enviado_hoy_centavos", Long.class)),
                        fila.get("dia_del_tope", LocalDate.class), fila.get("version", Long.class)))
                .one();
    }

    // ---- RegistroDeMovimientos ----

    @Override
    public Mono<Void> registrar(Movimiento movimiento) {
        return db.sql("insert into movimiento (cuenta_id, tipo, centavos, fecha, referencia) "
                        + "values (:cuenta, :tipo, :centavos, :fecha, :referencia)")
                .bind("cuenta", movimiento.cuentaId())
                .bind("tipo", movimiento.tipo().name())
                .bind("centavos", movimiento.valor().centavos())
                .bind("fecha", movimiento.fecha())
                .bind("referencia", movimiento.referencia())
                .then();
    }

    @Override
    public Flux<Movimiento> de(String cuentaId) {
        return db.sql("select tipo, centavos, fecha, referencia from movimiento where cuenta_id = :cuenta order by id")
                .bind("cuenta", cuentaId)
                .map(fila -> new Movimiento(cuentaId, Movimiento.Tipo.valueOf(fila.get("tipo", String.class)),
                        new Dinero(fila.get("centavos", Long.class)), fila.get("fecha", Instant.class),
                        fila.get("referencia", String.class)))
                .all();
    }

    // ---- OrdenesProcesadas ----

    @Override
    public Mono<Boolean> registrarSiEsNueva(String clave) {
        return db.sql("insert into orden_procesada (clave) values (:clave) on conflict do nothing")
                .bind("clave", clave)
                .fetch().rowsUpdated()
                .map(filas -> filas == 1);
    }

    // ---- PublicadorDeEventos: la bandeja de salida ----

    @Override
    public Mono<Void> publicar(EventoDeCuenta evento) {
        return db.sql("insert into evento_pendiente (id, tipo, cuenta_id, centavos, ocurrido_en) "
                        + "values (:id, :tipo, :cuenta, :centavos, :cuando)")
                .bind("id", evento.id())
                .bind("tipo", evento.tipo())
                .bind("cuenta", evento.cuentaId())
                .bind("centavos", evento.centavos())
                .bind("cuando", evento.ocurridoEn())
                .then();
    }
}
```

El adaptador de entrada solo cambia en que ya no espera:

<!-- file: ejemplo/despues/src/main/java/banco/adaptadores/entrada/webflux/ControladorDeCuentas.java -->
```java
package banco.adaptadores.entrada.webflux;

import banco.adaptadores.entrada.webflux.Peticiones.Apertura;
import banco.adaptadores.entrada.webflux.Peticiones.Deposito;
import banco.adaptadores.entrada.webflux.Peticiones.Transferencia;
import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.reactiva.puertos.entrada.AbrirCuenta;
import banco.aplicacion.reactiva.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.reactiva.puertos.entrada.Depositar;
import banco.aplicacion.reactiva.puertos.entrada.TransferirDinero;
import banco.aplicacion.reactiva.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.reactiva.puertos.entrada.TransferirDinero.Resultado;
import banco.dominio.Movimiento;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Traduce HTTP a los puertos reactivos. No espera nada: devuelve lo que los puertos prometen. */
@RestController
public class ControladorDeCuentas {

    private final AbrirCuenta abrirCuenta;
    private final Depositar depositar;
    private final TransferirDinero transferirDinero;
    private final ConsultarCuenta consultarCuenta;

    public ControladorDeCuentas(AbrirCuenta abrirCuenta, Depositar depositar, TransferirDinero transferirDinero,
                                ConsultarCuenta consultarCuenta) {
        this.abrirCuenta = abrirCuenta;
        this.depositar = depositar;
        this.transferirDinero = transferirDinero;
        this.consultarCuenta = consultarCuenta;
    }

    @PostMapping("/cuentas")
    public Mono<ResponseEntity<VistaDeCuenta>> abrir(@RequestBody Apertura apertura) {
        return abrirCuenta.abrir(apertura.titular(), apertura.centavos())
                .map(cuenta -> ResponseEntity.status(HttpStatus.CREATED).body(cuenta));
    }

    @PostMapping("/cuentas/{id}/depositos")
    public Mono<VistaDeCuenta> depositar(@PathVariable String id, @RequestBody Deposito deposito) {
        return depositar.depositar(id, deposito.centavos());
    }

    @PostMapping("/transferencias")
    public Mono<ResponseEntity<Map<String, String>>> transferir(@RequestHeader("Idempotency-Key") String clave,
                                                                @RequestBody Transferencia transferencia) {
        return transferirDinero.transferir(new Orden(clave, transferencia.origen(), transferencia.destino(), transferencia.centavos()))
                .map(resultado -> ResponseEntity
                        .status(resultado == Resultado.REALIZADA ? HttpStatus.CREATED : HttpStatus.OK)
                        .body(Map.of("resultado", resultado.name())));
    }

    @GetMapping("/cuentas/{id}")
    public Mono<VistaDeCuenta> consultar(@PathVariable String id) {
        return consultarCuenta.porId(id).switchIfEmpty(Mono.error(() -> new CuentaNoEncontrada(id)));
    }

    @GetMapping("/cuentas/{id}/movimientos")
    public Flux<Movimiento> movimientos(@PathVariable String id) {
        return consultarCuenta.movimientosDe(id);
    }
}
```

### El costo, contado

| | Opción C (este ejemplo) |
|---|---|
| `banco.dominio` | Sin cambios. Compilado desde el módulo hexagonal |
| `banco.aplicacion`: vistas, eventos, excepciones, `Reloj` | Sin cambios |
| `banco.aplicacion`: `ServicioDeCuentas` y 9 puertos | **Reescritos**: un servicio paralelo de 128 líneas y 9 interfaces nuevas, con la misma lógica en otro idioma |
| Adaptadores | Nuevos, como en cualquier cambio de infraestructura |
| Pruebas de la aplicación del módulo hexagonal | **No sirven**: prueban el servicio síncrono. Hay que reescribirlas con StepVerifier (sesión 08) |
| Dos servicios de aplicación con la misma lógica | Si conviven, cada cambio de negocio se hace dos veces. Si no, se borra el síncrono y con él las pruebas y adaptadores que lo usaban |

Y lo que se gana: el servicio atiende esperas largas de la base y de otros servicios sin ocupar hilos, y las rutas que no esperan no sufren por las que sí.

La lección de diseño: **el modelo de ejecución es parte del contrato de un puerto**. Un puerto síncrono declara, sin decirlo, "quien me implemente puede bloquear". Es una buena elección por defecto, porque es lo más simple. Pero cambiarla después cuesta la capa de aplicación entera, así que se decide al principio, con los números de la [sesión 01](../01-por-que-reactivo/) delante. Para el banco, con una base relacional y tráfico moderado, la opción A (MVC con hilos virtuales sobre el núcleo intacto) sería la recomendación razonable. La C se justifica cuando la mayor parte del tiempo de cada petición es espera de E/S y el volumen es alto.

## Errores comunes

- Llamar a un núcleo síncrono directamente desde WebFlux (el "antes"). Ningún error, ninguna advertencia; solo latencia con carga.
- Poner `Mono` en el dominio "para ser coherente".
- Implementar un puerto síncrono con R2DBC y `.block()`: dentro de un hilo de Netty, Reactor lo rechaza; fuera, bloquea igual que JDBC y con más piezas.
- Una unidad de trabajo reactiva que recibe un `Mono` ya armado en vez de un `Supplier`: el reintento vuelve a suscribirse al mismo trabajo, y si alguna parte se evaluó al ensamblar (un `Mono.just`, un `reloj.ahora()` fuera de la cadena), se repite con datos viejos.
- Leer las dos cuentas con `Mono.zip` dentro de la transacción: es la misma conexión; `zipWhen` lo hace en orden y lo dice.
- Mantener los dos servicios de aplicación "por si acaso" sin pruebas que los comparen.
- Arrancar a la vez dos aplicaciones que ejecutan el mismo `schema.sql`: una puede fallar con `duplicate key value violates unique constraint "pg_type_typname_nsp_index"`. Arráncalas una tras otra (pasó al preparar esta sesión).

## Taller

**Objetivo:** llevar tu servicio de pedidos a una arquitectura hexagonal reactiva, medir la diferencia y defender por escrito si valió la pena.

Sobre tu servicio de pedidos del [módulo hexagonal](../../hexagonal/) y lo que construiste en las sesiones 04 y 05.

1. Escribe un adaptador de entrada WebFlux sobre tus puertos **síncronos** con adaptadores JDBC, como el "antes". Comprueba que todo funciona.
2. Adapta `bloqueo.sh` a tu caso (un pedido cuyas existencias otra sesión tiene bloqueadas) y mide la latencia de una consulta ajena.
3. Escribe la opción B: el mismo núcleo, con cada llamada envuelta en `boundedElastic`. Mide otra vez. Explica por qué mejora y qué recurso consume ahora cada petición en espera.
4. Escribe la opción C: puertos reactivos en un paquete nuevo, el dominio **sin cambios** y compilado desde donde está, adaptadores R2DBC. Mide otra vez.
5. Tu dominio no debe importar nada de `reactor`. Tu aplicación reactiva no debe importar nada de `org.springframework`. Compruébalo con una prueba (la sesión 08 tiene una).
6. Cuenta lo que te costó la opción C como en la tabla "El costo, contado": archivos nuevos, líneas, pruebas que dejaron de servir.
7. Escribe la decisión para tu equipo: A, B o C para este servicio, con tus números de los pasos 2, 3 y 4.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| El dominio no cambió | `git diff` del paquete de dominio: vacío |
| Las tres variantes funcionan igual | El mismo conjunto de `curl` da las mismas respuestas |
| La consulta ajena no espera al bloqueo en la opción C | Medición del paso 4 |
| El costo de la opción C está contado | Tabla del paso 6 |
| La decisión cita números | `ANALISIS.md` |

**Entrega:** rama `taller/webflux-07`, con el código en `taller/solucion/` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Esta discusión aparece cada vez que un equipo con una arquitectura limpia y síncrona decide "pasarse a reactivo". La respuesta madura casi nunca es reescribir la aplicación: es preguntar qué problema se quiere resolver. Si es la escalabilidad ante esperas, desde Java 21 los hilos virtuales lo resuelven con el núcleo intacto. Si es streaming, una pasarela o una composición intensa de llamadas, entonces el servicio nuevo nace reactivo desde los puertos, y el dominio se mantiene puro como aquí. Lo que sí se ve en proyectos reales, y conviene evitar, es la mezcla del "antes": una fachada WebFlux sobre servicios que bloquean, elegida por moda, que rinde peor que el MVC al que sustituyó.

## Preguntas senior

1. ¿Por qué cambiar de JPA a R2DBC no es "solo cambiar un adaptador" en una arquitectura hexagonal?
2. ¿Pondrías `Mono` en el dominio? ¿Y en un puerto sin E/S como el reloj? Justifica las dos respuestas.
3. ¿Por qué la unidad de trabajo reactiva recibe un `Supplier<Mono<T>>` y no un `Mono<T>`?
4. Compara las opciones A, B y C para un servicio con una base PostgreSQL y 200 peticiones por segundo.
5. ¿Qué parte del contrato de un puerto no está escrita en su firma?
6. Tu equipo quiere mantener el servicio síncrono y el reactivo a la vez durante una migración. ¿Qué riesgos ves y cómo los controlas?
