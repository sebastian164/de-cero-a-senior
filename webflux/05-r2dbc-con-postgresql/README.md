# 05 — R2DBC con PostgreSQL: la base de datos sin bloquear

> Requiere la ruta de [HikariCP](../../hikaricp/) o, al menos, saber qué es un pool de conexiones y una transacción ([Nivel 8](../../java/nivel-08-bases-de-datos/)).

## Idea

Llamar a la base con JDBC es como hacer un pedido por teléfono y quedarte en línea hasta que te digan "listo". Con R2DBC dejas el pedido y te devuelven la llamada. La cocina es la misma, la comida tarda lo mismo; lo que cambia es que tu teléfono queda libre mientras tanto.

Una consecuencia menos obvia: si tú no estás al teléfono, la cocina no puede saber "quién eres" mirando la línea. Hay que apuntar el pedido con un número. Eso le pasa a la transacción.

## Lo esencial

### Por qué JDBC y JPA no sirven en WebFlux

| | JDBC / JPA | R2DBC |
|---|---|---|
| Una consulta | Bloquea el hilo hasta que llegan las filas | Devuelve un `Publisher`; las filas llegan cuando llegan |
| La transacción | Atada al **hilo** (un `ThreadLocal` con la conexión) | Viaja en el **contexto de la suscripción** de Reactor |
| Contexto de persistencia, carga perezosa | Sí (JPA) | No existe. Lo que quieres, lo pides |
| Pool | HikariCP | `r2dbc-pool` |

Que JDBC bloquee ya lo descalifica: es la sesión 03. Pero hay un segundo motivo. En una cadena reactiva, el paso 1 puede ejecutarse en un hilo y el paso 2 en otro. Una transacción guardada en un `ThreadLocal` se pierde en el cambio, y si la "arreglas" con `boundedElastic` para todo, sigues ocupando un hilo por transacción abierta. JPA añade más: la carga perezosa dispara consultas al tocar una relación, en el hilo que la toque, sin que nadie lo vea.

Si de verdad necesitas JPA, hay dos caminos honestos: MVC con hilos virtuales, o [Hibernate Reactive](https://hibernate.org/reactive/), que es otro producto con otra API. Lo que no funciona es WebFlux con JPA "envuelto".

### Qué es R2DBC

Una especificación (como JDBC lo es) de acceso no bloqueante a bases relacionales, sobre Reactive Streams. El controlador de PostgreSQL es `org.postgresql:r2dbc-postgresql`. Spring Data R2DBC añade encima:

| Pieza | Para qué |
|---|---|
| `DatabaseClient` | SQL a mano con parámetros con nombre: `db.sql("...").bind("id", id).map(...).one()` |
| `ReactiveCrudRepository<T, ID>` | El repositorio de Spring Data, con `Mono` y `Flux`. Consultas derivadas del nombre del método |
| `R2dbcEntityTemplate` | Operaciones con entidades sin escribir SQL |
| `TransactionalOperator` y `@Transactional` | Transacciones reactivas sobre `R2dbcTransactionManager` |

Spring Data R2DBC **no es un ORM**: no hay relaciones, ni carga perezosa, ni sincronización automática de cambios. Una fila es un objeto, y para guardar llamas a `save`. `@Version` sí funciona: el `UPDATE` lleva `where version = ?` y, si no encuentra la fila, termina con `OptimisticLockingFailureException`.

### Transacciones reactivas

```java
transacciones.transactional(trabajo)       // TransactionalOperator
```

Al suscribirse, el operador toma una conexión del pool, abre la transacción y la deja **en el contexto de Reactor** de esa suscripción. Cada `DatabaseClient` o repositorio que se ejecute dentro de la cadena la encuentra ahí, esté en el hilo que esté. Si la cadena termina bien, confirma; si termina con error, revierte; si alguien cancela la suscripción, también revierte.

`@Transactional` sobre un método que devuelve `Mono` o `Flux` hace lo mismo: Spring detecta el tipo de retorno y usa el gestor reactivo. La condición es la de siempre: el método tiene que **devolver** la cadena, no suscribirse dentro.

### El pool

El pool de R2DBC (`spring.r2dbc.pool.*`) tiene las mismas reglas que HikariCP: tamaño, tiempo máximo de espera, validación. Con WebFlux es fácil aceptar diez mil peticiones a la vez; con veinte conexiones, nueve mil novecientas ochenta esperan a una. **Lo reactivo no fabrica conexiones.** La cuenta de la [sesión 02 de HikariCP](../../hikaricp/02-configuracion-que-importa/) sigue valiendo.

## Ejemplo

Un servicio de cuentas con WebFlux y PostgreSQL. Las dos versiones tienen las mismas rutas y el mismo SQL. El "resumen" de una cuenta incluye `pg_sleep(0.2)`: simula un informe pesado de 200 ms en la base. Las dos fijan cuatro hilos de bucle y un pool de 20 conexiones.

Usan la base `cuentas` del [entorno](../entorno/) (PostgreSQL en el puerto 5433), y crean sus tablas al arrancar:

<!-- file: ejemplo/despues/src/main/resources/schema.sql -->
```sql
create table if not exists cuenta (
    id              varchar(64)  primary key,
    titular         varchar(120) not null,
    saldo_centavos  bigint       not null check (saldo_centavos >= 0),
    version         bigint       not null
);

create table if not exists movimiento (
    id          bigint generated always as identity primary key,
    cuenta_id   varchar(64)  not null references cuenta (id),
    tipo        varchar(32)  not null,
    centavos    bigint       not null check (centavos > 0),
    fecha       timestamp with time zone not null,
    referencia  varchar(120) not null
);
```

### Antes

WebFlux por fuera, JDBC y HikariCP por dentro.

<!-- file: ejemplo/antes/src/main/java/banco/cuentas/AlmacenJdbc.java -->
```java
package banco.cuentas;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC: cada método bloquea el hilo que lo llama hasta que PostgreSQL responde. */
@Repository
public class AlmacenJdbc {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacciones;

    public AlmacenJdbc(JdbcTemplate jdbc, TransactionTemplate transacciones) {
        this.jdbc = jdbc;
        this.transacciones = transacciones;
    }

    public Cuenta abrir(String titular, long centavos) {
        Cuenta cuenta = new Cuenta("cuenta-" + UUID.randomUUID(), titular, centavos, 1);
        jdbc.update("insert into cuenta (id, titular, saldo_centavos, version) values (?, ?, ?, ?)",
                cuenta.id(), cuenta.titular(), cuenta.saldoCentavos(), cuenta.version());
        return cuenta;
    }

    public Cuenta buscar(String id) {
        return jdbc.query("select id, titular, saldo_centavos, version from cuenta where id = ?",
                        (fila, n) -> new Cuenta(fila.getString("id"), fila.getString("titular"),
                                fila.getLong("saldo_centavos"), fila.getLong("version")), id)
                .stream().findFirst()
                .orElseThrow(() -> new CuentaNoEncontrada(id));
    }

    public Resumen resumen(String id) {
        // pg_sleep(0.2) hace de informe pesado: la consulta tarda 200 ms en la base
        return jdbc.query("""
                        select c.id, c.titular, c.saldo_centavos,
                               (select count(*) from movimiento m where m.cuenta_id = c.id) as movimientos
                        from cuenta c cross join pg_sleep(0.2)
                        where c.id = ?""",
                        (fila, n) -> new Resumen(fila.getString("id"), fila.getString("titular"),
                                fila.getLong("saldo_centavos"), fila.getLong("movimientos"), Thread.currentThread().getName()), id)
                .stream().findFirst()
                .orElseThrow(() -> new CuentaNoEncontrada(id));
    }

    /** Todo o nada, con la transacción de Spring atada al hilo que la ejecuta. */
    public void transferir(String origen, String destino, long centavos) {
        transacciones.executeWithoutResult(estado -> {
            int debitadas = jdbc.update("update cuenta set saldo_centavos = saldo_centavos - ?, version = version + 1 "
                    + "where id = ? and saldo_centavos >= ?", centavos, origen, centavos);
            if (debitadas == 0) {
                buscar(origen);                                   // lanza CuentaNoEncontrada si no existe
                throw new SaldoInsuficiente(origen);
            }
            int acreditadas = jdbc.update("update cuenta set saldo_centavos = saldo_centavos + ?, version = version + 1 "
                    + "where id = ?", centavos, destino);
            if (acreditadas == 0) {
                throw new CuentaNoEncontrada(destino);            // revierte también el débito
            }
            Timestamp ahora = Timestamp.from(Instant.now());
            String insertar = "insert into movimiento (cuenta_id, tipo, centavos, fecha, referencia) values (?, ?, ?, ?, ?)";
            jdbc.update(insertar, origen, "TRANSFERENCIA_ENVIADA", centavos, ahora, "a " + destino);
            jdbc.update(insertar, destino, "TRANSFERENCIA_RECIBIDA", centavos, ahora, "de " + origen);
        });
    }
}
```

<!-- file: ejemplo/antes/src/main/java/banco/cuentas/ControladorDeCuentas.java -->
```java
package banco.cuentas;

import banco.cuentas.Peticiones.Apertura;
import banco.cuentas.Peticiones.Transferencia;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Firmas reactivas, cuerpos bloqueantes: Mono.just(...) evalúa la llamada JDBC en el hilo del bucle. */
@RestController
public class ControladorDeCuentas {

    private final AlmacenJdbc almacen;

    public ControladorDeCuentas(AlmacenJdbc almacen) {
        this.almacen = almacen;
    }

    @PostMapping("/cuentas")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Cuenta> abrir(@RequestBody Apertura apertura) {
        return Mono.just(almacen.abrir(apertura.titular(), apertura.centavos()));
    }

    @GetMapping("/cuentas/{id}")
    public Mono<Cuenta> consultar(@PathVariable String id) {
        return Mono.just(almacen.buscar(id));
    }

    @GetMapping("/cuentas/{id}/resumen")
    public Mono<Resumen> resumen(@PathVariable String id) {
        return Mono.just(almacen.resumen(id));
    }

    @PostMapping("/transferencias")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> transferir(@RequestBody Transferencia transferencia) {
        almacen.transferir(transferencia.origen(), transferencia.destino(), transferencia.centavos());
        return Mono.empty();
    }
}
```

### Después

R2DBC, Spring Data y `TransactionalOperator`.

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
    <artifactId>s05-despues</artifactId>
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
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

<!-- file: ejemplo/despues/src/main/resources/application.yaml -->
```yaml
server:
  port: 8093

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5433/cuentas
    username: banco
    password: banco
    pool:
      max-size: 20                 # el mismo tamaño que el pool de HikariCP de la versión anterior
  sql:
    init:
      mode: always                 # también funciona con R2DBC: ejecuta schema.sql al arrancar
```

<!-- file: ejemplo/despues/src/main/java/banco/cuentas/Cuenta.java -->
```java
package banco.cuentas;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/** Una fila de la tabla cuenta. Spring Data R2DBC la lee y la escribe; @Version da el bloqueo optimista. */
@Table("cuenta")
public record Cuenta(@Id String id, String titular, long saldoCentavos, @Version Long version) {

    public Cuenta conSaldo(long nuevoSaldo) {
        return new Cuenta(id, titular, nuevoSaldo, version);
    }
}
```

<!-- file: ejemplo/despues/src/main/java/banco/cuentas/RepositorioDeCuentas.java -->
```java
package banco.cuentas;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/** Un repositorio reactivo: Spring Data escribe la implementación y cada método devuelve Mono o Flux. */
public interface RepositorioDeCuentas extends ReactiveCrudRepository<Cuenta, String> {
}
```

<!-- file: ejemplo/despues/src/main/java/banco/cuentas/ServicioDeCuentas.java -->
```java
package banco.cuentas;

import java.time.Instant;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** R2DBC: ningún método bloquea. Devuelven la promesa del resultado y el hilo sigue atendiendo a otros. */
@Service
public class ServicioDeCuentas {

    private final RepositorioDeCuentas cuentas;
    private final RepositorioDeMovimientos movimientos;
    private final DatabaseClient baseDeDatos;
    private final TransactionalOperator transacciones;

    public ServicioDeCuentas(RepositorioDeCuentas cuentas, RepositorioDeMovimientos movimientos,
                             DatabaseClient baseDeDatos, TransactionalOperator transacciones) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.baseDeDatos = baseDeDatos;
        this.transacciones = transacciones;
    }

    public Mono<Cuenta> abrir(String titular, long centavos) {
        return cuentas.save(new Cuenta("cuenta-" + UUID.randomUUID(), titular, centavos, null));   // version null: es nueva
    }

    public Mono<Cuenta> buscar(String id) {
        return cuentas.findById(id).switchIfEmpty(Mono.error(new CuentaNoEncontrada(id)));
    }

    public Flux<Movimiento> movimientosDe(String id) {
        return movimientos.findByCuentaIdOrderById(id);
    }

    /** SQL a mano con DatabaseClient, para lo que no es un CRUD. Misma consulta de 200 ms que con JDBC. */
    public Mono<Resumen> resumen(String id) {
        return baseDeDatos.sql("""
                        select c.id, c.titular, c.saldo_centavos,
                               (select count(*) from movimiento m where m.cuenta_id = c.id) as movimientos
                        from cuenta c cross join pg_sleep(0.2)
                        where c.id = :id""")
                .bind("id", id)
                .map(fila -> new Resumen(fila.get("id", String.class), fila.get("titular", String.class),
                        fila.get("saldo_centavos", Long.class), fila.get("movimientos", Long.class),
                        Thread.currentThread().getName()))
                .one()
                .switchIfEmpty(Mono.error(new CuentaNoEncontrada(id)));
    }

    /**
     * Todo o nada. La transacción no puede ir atada al hilo, porque la operación pasa por varios:
     * viaja en el contexto de Reactor, y TransactionalOperator la abre al suscribirse y la cierra al terminar.
     */
    public Mono<Void> transferir(String origenId, String destinoId, long centavos) {
        Mono<Void> trabajo = buscar(origenId)
                .flatMap(origen -> origen.saldoCentavos() < centavos
                        ? Mono.<Cuenta>error(new SaldoInsuficiente(origenId))
                        : cuentas.save(origen.conSaldo(origen.saldoCentavos() - centavos)))
                .then(buscar(destinoId))
                .flatMap(destino -> cuentas.save(destino.conSaldo(destino.saldoCentavos() + centavos)))
                .then(Mono.defer(() -> {
                    Instant ahora = Instant.now();
                    return movimientos.save(new Movimiento(null, origenId, "TRANSFERENCIA_ENVIADA", centavos, ahora, "a " + destinoId))
                            .then(movimientos.save(new Movimiento(null, destinoId, "TRANSFERENCIA_RECIBIDA", centavos, ahora, "de " + origenId)));
                }))
                .then();
        return transacciones.transactional(trabajo);
    }
}
```

El controlador es el de antes, con el servicio en lugar del almacén y una ruta más para los movimientos:

<!-- file: ejemplo/despues/src/main/java/banco/cuentas/ControladorDeCuentas.java -->
```java
package banco.cuentas;

import banco.cuentas.Peticiones.Apertura;
import banco.cuentas.Peticiones.Transferencia;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Las mismas rutas que la versión con JDBC. Lo que cambió está debajo. */
@RestController
public class ControladorDeCuentas {

    private final ServicioDeCuentas servicio;

    public ControladorDeCuentas(ServicioDeCuentas servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/cuentas")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Cuenta> abrir(@RequestBody Apertura apertura) {
        return servicio.abrir(apertura.titular(), apertura.centavos());
    }

    @GetMapping("/cuentas/{id}")
    public Mono<Cuenta> consultar(@PathVariable String id) {
        return servicio.buscar(id);
    }

    @GetMapping("/cuentas/{id}/movimientos")
    public Flux<Movimiento> movimientos(@PathVariable String id) {
        return servicio.movimientosDe(id);
    }

    @GetMapping("/cuentas/{id}/resumen")
    public Mono<Resumen> resumen(@PathVariable String id) {
        return servicio.resumen(id);
    }

    @PostMapping("/transferencias")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> transferir(@RequestBody Transferencia transferencia) {
        return servicio.transferir(transferencia.origen(), transferencia.destino(), transferencia.centavos());
    }
}
```

### Ejecutarlo y medir

```bash
cd ../entorno && docker compose -p webflux up -d && cd -
cd ejemplo/antes && mvn -q spring-boot:run        # puerto 8092, en una terminal
cd ejemplo/despues && mvn -q spring-boot:run      # puerto 8093, en otra
```

Con bash, para cada puerto:

```bash
P=8092
ANA=$(curl -s -X POST localhost:$P/cuentas -H 'Content-Type: application/json' -d '{"titular":"Ana","centavos":100000}' | sed -E 's/.*"id":"([^"]+)".*/\1/')
LUIS=$(curl -s -X POST localhost:$P/cuentas -H 'Content-Type: application/json' -d '{"titular":"Luis","centavos":0}' | sed -E 's/.*"id":"([^"]+)".*/\1/')

curl -s -w ' [%{http_code}]\n' -X POST localhost:$P/transferencias -H 'Content-Type: application/json' -d "{\"origen\":\"$ANA\",\"destino\":\"$LUIS\",\"centavos\":30000}"
curl -s -w ' [%{http_code}]\n' -X POST localhost:$P/transferencias -H 'Content-Type: application/json' -d "{\"origen\":\"$ANA\",\"destino\":\"cuenta-que-no-existe\",\"centavos\":10000}"
curl -s localhost:$P/cuentas/$ANA; echo
curl -s localhost:$P/cuentas/$LUIS/resumen; echo

# veinte informes a la vez; la primera ronda incluye el calentamiento de los pools: mide la segunda
time (seq 20 | xargs -P 20 -I{} curl -s -o /dev/null localhost:$P/cuentas/$ANA/resumen)
time (seq 20 | xargs -P 20 -I{} curl -s -o /dev/null localhost:$P/cuentas/$ANA/resumen)
```

Lo funcional es idéntico en las dos:

```
 [201]
{"detail":"no existe la cuenta cuenta-que-no-existe","instance":"/transferencias","status":404,"title":"Not Found"} [404]
{"id":"cuenta-5971a126-...","titular":"Ana","saldoCentavos":70000,"version":1}
```

Ana tiene 70.000: la transferencia a una cuenta inexistente **ya le había descontado** 10.000 cuando falló el abono, y la transacción lo revirtió. En la versión R2DBC, sin un solo `ThreadLocal`.

Lo que cambia:

| | Antes (8092, JDBC) | Después (8093, R2DBC) |
|---|---|---|
| 20 informes de 200 ms a la vez (segunda ronda) | 1,10 s | 0,25 s |
| Hilo en el que llegó el resultado (`"hilo"` del resumen) | `reactor-http-nio-2`: un hilo del bucle HTTP, que esperó la consulta | `reactor-tcp-nio-3`: el hilo de red del controlador R2DBC, que solo recibió las filas |

### Análisis

| Lo que se ve | Por qué |
|---|---|
| 1,10 s con JDBC | Cuatro hilos de bucle, cinco informes cada uno, uno detrás de otro. El pool de HikariCP tenía 20 conexiones libres; nadie pudo usarlas porque los hilos que tenían que pedirlas estaban dormidos |
| 0,25 s con R2DBC | Veinte consultas en vuelo a la vez en veinte conexiones, sin ningún hilo esperando |
| El resultado JDBC llega en un hilo `reactor-http-nio` | `Mono.just(almacen.resumen(id))` ejecutó la consulta en el bucle, antes de crear el `Mono` |
| La transferencia funciona igual en las dos | La corrección no era el problema: la versión JDBC es correcta y lenta bajo carga. Por eso este error pasa las pruebas funcionales y llega a producción |
| `version` 2 frente a 1 | En la versión JDBC el SQL escrito a mano suma 1 en cada cambio y empieza en 1; Spring Data empieza en 0. Da igual el número: importa que cambie |

## Errores comunes

- Añadir `spring-boot-starter-data-jpa` o `spring-boot-starter-jdbc` a un proyecto WebFlux "porque ya lo conozco".
- Envolver el repositorio JPA en `Mono.fromCallable(...).subscribeOn(boundedElastic())` y llamarlo reactivo. Funciona, pero tienes un hilo por consulta y las transacciones de JPA atadas a hilos que cambian.
- `@Transactional` en un método que se suscribe dentro (`.subscribe()`) en vez de devolver la cadena: la transacción se confirma antes de que ocurra nada.
- Lanzar dos consultas a la vez (`Mono.zip`) dentro de una transacción: todas usan la misma conexión, que atiende una consulta tras otra, así que no ganas nada y el orden queda en manos del controlador. Dentro de una transacción, en orden (`flatMap`, `zipWhen`, `then`).
- Esperar relaciones y carga perezosa de Spring Data R2DBC.
- Interfaces de repositorio anidadas dentro de una clase: Spring Data no las detecta por defecto (`considerNestedRepositories`).
- Pool de R2DBC con el tamaño por defecto (10) y miles de peticiones concurrentes: la espera se traslada al pool.
- Dos aplicaciones que arrancan a la vez y ejecutan el mismo `schema.sql`: PostgreSQL puede rechazar uno de los `create table if not exists` simultáneos (`duplicate key value violates unique constraint "pg_type_typname_nsp_index"`). En producción, el esquema lo aplican las migraciones (Flyway, Liquibase), una vez.

## Taller

**Objetivo:** llevar la persistencia de tu servicio de pedidos a R2DBC, con transacciones reactivas y un argumento medido de por qué JDBC no valía.

Sobre el servicio WebFlux de tu taller de la [sesión 04](../04-webflux/). Usa la base `cuentas` del entorno o crea otra.

1. Escribe el esquema a mano: pedidos, líneas de pedido, existencias y claves de idempotencia.
2. Implementa la persistencia **dos veces**: con `JdbcTemplate` llamado directamente en los manejadores, y con R2DBC (`DatabaseClient` o repositorios reactivos; decide y justifica).
3. "Confirmar pedido" descuenta existencias de varios productos y cambia el estado del pedido, todo o nada. Hazlo con `TransactionalOperator` en la versión R2DBC.
4. Provoca un fallo a mitad (el tercer producto no tiene existencias) y comprueba en la base que no se descontó nada del primero ni del segundo.
5. Mide 40 confirmaciones concurrentes en las dos versiones, con cuatro hilos de bucle y el mismo tamaño de pool. Añade una consulta lenta artificial si tus consultas son demasiado rápidas para ver la diferencia, y dilo.
6. Reduce el pool de R2DBC a 5 conexiones (baja también `initial-size`, que es 10 por omisión: con `max-size` menor la aplicación no arranca, `maxSize must be greater than or equal to initialSize`) y repite. ¿Qué limita ahora? ¿Qué ves en `pg_stat_activity`?
7. Dos confirmaciones simultáneas por la última unidad de un producto: solo una debe tener éxito. Demuéstralo.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| La confirmación es atómica | Paso 4, con la consulta a la base en `ANALISIS.md` |
| No hay JDBC ni JPA en la versión R2DBC | Dependencias del `pom.xml` |
| La diferencia de rendimiento está medida y explicada | Pasos 5 y 6 |
| La última unidad se vende una vez | Paso 7 |

**Entrega:** rama `taller/webflux-05`, con el código en `taller/solucion/` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

R2DBC es maduro para PostgreSQL, MySQL, MariaDB, SQL Server y Oracle, pero su ecosistema es menor que el de JDBC: menos herramientas, menos ejemplos y ningún ORM completo. Las migraciones siguen siendo JDBC (Flyway y Liquibase no tienen versión reactiva): en un servicio R2DBC es normal tener el controlador JDBC solo para migrar al arrancar, fuera del camino de las peticiones. Lo que más se ve en revisión es SQL bien escrito con `DatabaseClient` para todo lo que no sea un CRUD trivial, y transacciones cortas declaradas en la capa de aplicación. Y una advertencia práctica: tras un reinicio de la base, el pool puede tener conexiones muertas que fallan una vez antes de descartarse (al preparar este curso se vio un `CannotCreateTransactionException` así); configura validación y `max-idle-time` del pool.

## Preguntas senior

1. Da dos motivos, distintos del bloqueo, por los que JPA no encaja en una cadena reactiva.
2. ¿Dónde vive una transacción reactiva, si no es en el hilo? ¿Qué pasa si cancelas la suscripción a mitad?
3. ¿Qué diferencia hay entre Spring Data R2DBC y JPA en cómo se guardan los cambios de una entidad?
4. Tienes 10.000 peticiones concurrentes y un pool R2DBC de 20 conexiones. ¿Qué ganaste respecto a MVC con HikariCP y 20 conexiones?
5. ¿Por qué no se lanzan consultas en paralelo dentro de una transacción?
6. ¿Cuándo preferirías MVC con hilos virtuales y JPA a WebFlux con R2DBC para un servicio nuevo?
