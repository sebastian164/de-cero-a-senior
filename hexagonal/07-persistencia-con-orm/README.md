# 07 — Persistencia con ORM

> **Sobre el código de esta sesión.** El núcleo del banco se compiló y se ejecutó. El proyecto de esta sesión, [`proyecto-jpa`](../proyecto-jpa/), usa Spring Boot, Hibernate y PostgreSQL, y **no se compiló ni se ejecutó al escribir el curso**: el entorno donde se preparó no tenía acceso a esas dependencias. Trátalo como una guía: constrúyelo en tu máquina y, si algo no encaja con tu versión, corrígelo. Ese ajuste es parte del taller.

## Idea

Un traductor simultáneo. Tú hablas de cuentas, transferencias y saldos; la base de datos habla de filas, columnas y claves. El traductor escucha en un idioma y repite en el otro. Un buen traductor es invisible, pero no es mágico: si no sabes nada del otro idioma, no notarás cuándo traduce mal ni cuánto tarda.

Un ORM es ese traductor entre objetos y tablas. Y en una arquitectura hexagonal tiene un sitio muy concreto: **es un detalle de un adaptador de salida**.

## Lo esencial

### Qué hace un ORM

| Pieza | Qué es | Con qué se corresponde en este módulo |
|---|---|---|
| Entidad (`@Entity`) | Una clase cuyos objetos se guardan como filas | El modelo de persistencia |
| Contexto de persistencia | La memoria de trabajo del ORM durante una transacción: recuerda lo que leyó | El "cuaderno" de `AlmacenEnMemoria` |
| Detección de cambios | Al confirmar, compara cada entidad con lo que leyó y genera los `UPDATE` | — |
| `@Version` | Bloqueo optimista: añade `AND version = ?` a cada `UPDATE` | `exigirVersionVigente` |
| JPQL | Consultas sobre entidades en lugar de tablas | — |

JPA es la especificación; Hibernate, la implementación que trae Spring Boot.

### Dónde va en la arquitectura

```
aplicación ──► RepositorioDeCuentas (puerto)
                      ▲
                      │ implementa
               AlmacenJpa (adaptador) ──► EntityManager ──► PostgreSQL
```

El núcleo de las sesiones 02 a 05 **no cambia ni una línea**. Se añade un adaptador nuevo y se conecta en otra raíz de composición.

### Un modelo o dos

La decisión más discutida. ¿Las anotaciones de JPA van en `Cuenta` o en otra clase?

| | Anotar el dominio | Entidades de persistencia aparte |
|---|---|---|
| Clases | Una | Dos, más la traducción entre ellas |
| El dominio depende de | JPA | Nada |
| Lo que JPA exige (constructor vacío, clases no finales, campos mutables) | Se cuela en el dominio | Se queda en el adaptador |
| Cambiar el esquema sin tocar reglas | Difícil | Fácil |
| Coste | Poco código | Código repetitivo de traducción |
| Encaja con | Dominios sencillos, equipos pequeños | Dominios con reglas, sistemas que duran |

Este módulo usa **dos modelos**, porque es el caso que hay que saber hacer. `Cuenta` es final, valida en su fábrica y no tiene constructor vacío; `CuentaJpa` es lo que Hibernate necesita. En el trabajo real, anotar el dominio es una decisión legítima si se toma sabiendo lo que cuesta.

### El contexto de persistencia

Tres comportamientos que sorprenden a quien llega de JDBC:

| Comportamiento | Consecuencia |
|---|---|
| Leer dos veces la misma fila en una transacción devuelve **el mismo objeto** | No hay segunda consulta; tampoco ves cambios de otros |
| No hace falta llamar a "guardar" para actualizar: basta modificar la entidad leída | Los `UPDATE` salen solos al confirmar |
| Las sentencias se envían **al confirmar**, no cuando escribes el código | Los errores de la base (versión, restricciones) saltan al final |

El tercero explica una decisión del adaptador: el conflicto de versión no aparece en `guardar`, sino al confirmar la transacción. Por eso la traducción a `ConflictoDeConcurrencia` está en `UnidadDeTrabajoJpa`.

### Lo que el ORM no resuelve

- **No reintenta.** Ante un conflicto de versión lanza una excepción; reintentar sigue siendo del caso de uso.
- **No elige el nivel de aislamiento.** Lo decide la base de datos.
- **No evita consultas de más.** Las relaciones perezosas recorridas en un bucle generan una consulta por elemento (N+1).
- **No sustituye a SQL.** Para algunas cosas, una sentencia nativa es lo correcto. Aquí, anotar la clave de idempotencia.

## El proyecto

```
proyecto-jpa/
  pom.xml                      compila también ../proyecto/src (el núcleo), sin sus adaptadores
  compose.yaml                 PostgreSQL
  src/main/resources/
    application.yaml
    schema.sql
  src/main/java/banco/jpa/
    AplicacionBanco.java       arranque
    Configuracion.java         la raíz de composición
    CuentaJpa.java             entidades de persistencia
    MovimientoJpa.java
    EventoPendienteJpa.java
    AlmacenJpa.java            adaptador: cuatro puertos de salida
    UnidadDeTrabajoJpa.java    adaptador: el puerto de transacciones
    ControladorDeCuentas.java  adaptador de entrada
    TraductorDeErrores.java
```

### Construcción

Antes de usarlo, comprueba cuál es la última versión estable de Spring Boot y ajusta el `pom.xml`.

<!-- file: ../proyecto-jpa/pom.xml -->
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>

    <groupId>curso.hexagonal</groupId>
    <artifactId>banco-jpa</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>21</java.version>
        <start-class>banco.jpa.AplicacionBanco</start-class>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- El núcleo no se copia: se compila desde donde está. -->
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
                                <source>../proyecto/src</source>
                            </sources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <!-- Del proyecto anterior entran el dominio y la aplicación. Sus adaptadores y su Main se quedan fuera. -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>banco/adaptadores/**</exclude>
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

<!-- file: ../proyecto-jpa/compose.yaml -->
```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: banco
      POSTGRES_USER: banco
      POSTGRES_PASSWORD: banco
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U banco -d banco"]
      interval: 5s
      timeout: 3s
      retries: 10
```

<!-- file: ../proyecto-jpa/src/main/resources/application.yaml -->
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/banco
    username: banco
    password: banco
  jpa:
    open-in-view: false          # la transacción termina en el caso de uso, no en la vista
    hibernate:
      ddl-auto: none             # el esquema es nuestro, no lo inventa Hibernate
    properties:
      hibernate:
        format_sql: true
  sql:
    init:
      mode: always               # ejecuta schema.sql al arrancar

logging:
  level:
    org.hibernate.SQL: debug     # para ver cada sentencia mientras aprendes
```

### El esquema

Escrito a mano, para ver qué hay de verdad en la base.

<!-- file: ../proyecto-jpa/src/main/resources/schema.sql -->
```sql
create table if not exists cuenta (
    id                    varchar(64)  primary key,
    titular               varchar(120) not null,
    saldo_centavos        bigint       not null check (saldo_centavos >= 0),
    enviado_hoy_centavos  bigint       not null check (enviado_hoy_centavos >= 0),
    dia_del_tope          date         not null,
    version               bigint       not null
);

create table if not exists movimiento (
    id          bigint generated always as identity primary key,
    cuenta_id   varchar(64)  not null references cuenta (id),
    tipo        varchar(32)  not null,
    centavos    bigint       not null check (centavos > 0),
    fecha       timestamp with time zone not null,
    referencia  varchar(120) not null
);

create index if not exists movimiento_por_cuenta on movimiento (cuenta_id, id);

-- La clave primaria es lo que impide atender dos veces la misma orden.
create table if not exists orden_procesada (
    clave  varchar(120) primary key
);

-- La bandeja de salida.
create table if not exists evento_pendiente (
    id           varchar(160) primary key,
    tipo         varchar(64)  not null,
    cuenta_id    varchar(64)  not null,
    centavos     bigint       not null,
    ocurrido_en  timestamp with time zone not null
);
```

El `check (saldo_centavos >= 0)` repite una regla del dominio. No es duplicación inútil: es la última defensa si algún día alguien escribe en la tabla sin pasar por el dominio.

### Las entidades de persistencia

<!-- file: ../proyecto-jpa/src/main/java/banco/jpa/CuentaJpa.java -->
```java
package banco.jpa;

import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;

/** La fila de una cuenta. No tiene reglas: solo datos y la traducción desde y hacia el dominio. */
@Entity
@Table(name = "cuenta")
public class CuentaJpa {

    @Id
    private String id;

    @Column(nullable = false)
    private String titular;

    @Column(name = "saldo_centavos", nullable = false)
    private long saldoCentavos;

    @Column(name = "enviado_hoy_centavos", nullable = false)
    private long enviadoHoyCentavos;

    @Column(name = "dia_del_tope", nullable = false)
    private LocalDate diaDelTope;

    @Version                                                     // Hibernate la comprueba y la incrementa en cada UPDATE
    private long version;

    protected CuentaJpa() {                                      // lo exige JPA
    }

    static CuentaJpa nueva(Cuenta cuenta) {
        CuentaJpa fila = new CuentaJpa();
        fila.id = cuenta.id();
        fila.copiarDesde(cuenta);
        return fila;
    }

    void copiarDesde(Cuenta cuenta) {
        this.titular = cuenta.titular();
        this.saldoCentavos = cuenta.saldo().centavos();
        this.enviadoHoyCentavos = cuenta.enviadoHoy().centavos();
        this.diaDelTope = cuenta.diaDelTope();
    }

    Cuenta aDominio() {
        return Cuenta.existente(id, titular, new Dinero(saldoCentavos), new Dinero(enviadoHoyCentavos), diaDelTope, version);
    }

    long version() {
        return version;
    }
}
```

<!-- file: ../proyecto-jpa/src/main/java/banco/jpa/MovimientoJpa.java -->
```java
package banco.jpa;

import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "movimiento")
public class MovimientoJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private String cuentaId;

    @Enumerated(EnumType.STRING)                                 // el nombre, no la posición: reordenar el enum no rompe los datos
    @Column(nullable = false)
    private Movimiento.Tipo tipo;

    @Column(nullable = false)
    private long centavos;

    @Column(nullable = false)
    private Instant fecha;

    @Column(nullable = false)
    private String referencia;

    protected MovimientoJpa() {
    }

    static MovimientoJpa desde(Movimiento movimiento) {
        MovimientoJpa fila = new MovimientoJpa();
        fila.cuentaId = movimiento.cuentaId();
        fila.tipo = movimiento.tipo();
        fila.centavos = movimiento.valor().centavos();
        fila.fecha = movimiento.fecha();
        fila.referencia = movimiento.referencia();
        return fila;
    }

    Movimiento aDominio() {
        return new Movimiento(cuentaId, tipo, new Dinero(centavos), fecha, referencia);
    }
}
```

<!-- file: ../proyecto-jpa/src/main/java/banco/jpa/EventoPendienteJpa.java -->
```java
package banco.jpa;

import banco.aplicacion.EventoDeCuenta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Una fila de la bandeja de salida. */
@Entity
@Table(name = "evento_pendiente")
public class EventoPendienteJpa {

    @Id
    private String id;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "cuenta_id", nullable = false)
    private String cuentaId;

    @Column(nullable = false)
    private long centavos;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    protected EventoPendienteJpa() {
    }

    static EventoPendienteJpa desde(EventoDeCuenta evento) {
        EventoPendienteJpa fila = new EventoPendienteJpa();
        fila.id = evento.id();
        fila.tipo = evento.tipo();
        fila.cuentaId = evento.cuentaId();
        fila.centavos = evento.centavos();
        fila.ocurridoEn = evento.ocurridoEn();
        return fila;
    }
}
```

### El adaptador de salida

Como `AlmacenEnMemoria`, implementa varios puertos porque detrás hay una sola base de datos.

<!-- file: ../proyecto-jpa/src/main/java/banco/jpa/AlmacenJpa.java -->
```java
package banco.jpa;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Movimiento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Los puertos de salida del banco, sobre JPA. Es el único sitio del sistema que sabe que hay un ORM. */
@Repository
public class AlmacenJpa implements RepositorioDeCuentas, RegistroDeMovimientos, OrdenesProcesadas, PublicadorDeEventos {

    @PersistenceContext                                          // el EntityManager de la transacción en curso
    private EntityManager entidades;

    // ---- RepositorioDeCuentas ----

    @Override
    public String siguienteId() {
        return "cuenta-" + UUID.randomUUID();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        CuentaJpa fila = entidades.find(CuentaJpa.class, cuenta.id());
        if (fila == null) {
            entidades.persist(CuentaJpa.nueva(cuenta));                  // INSERT al confirmar
            return;
        }
        if (fila.version() != cuenta.version()) {
            throw new ConflictoDeConcurrencia("la cuenta " + cuenta.id());   // se leyó en otra transacción y ya cambió
        }
        fila.copiarDesde(cuenta);     // sin "guardar": al confirmar, Hibernate ve el cambio y emite
                                      // UPDATE cuenta SET … , version = ? WHERE id = ? AND version = ?
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        return Optional.ofNullable(entidades.find(CuentaJpa.class, id)).map(CuentaJpa::aDominio);
    }

    // ---- RegistroDeMovimientos ----

    @Override
    public void registrar(Movimiento movimiento) {
        entidades.persist(MovimientoJpa.desde(movimiento));
    }

    @Override
    public List<Movimiento> de(String cuentaId) {
        return entidades.createQuery("select m from MovimientoJpa m where m.cuentaId = :cuenta order by m.id", MovimientoJpa.class)
                .setParameter("cuenta", cuentaId)
                .getResultList().stream().map(MovimientoJpa::aDominio).toList();
    }

    // ---- OrdenesProcesadas ----

    @Override
    public boolean registrarSiEsNueva(String clave) {
        // SQL nativo a propósito. Una sola sentencia atómica: si otra transacción está insertando la misma
        // clave, esta espera a que termine; si aquella confirma, aquí no se inserta nada y la orden es repetida.
        int insertadas = entidades.createNativeQuery("insert into orden_procesada (clave) values (:clave) on conflict do nothing")
                .setParameter("clave", clave)
                .executeUpdate();
        return insertadas == 1;
    }

    // ---- PublicadorDeEventos: la bandeja de salida ----

    @Override
    public void publicar(EventoDeCuenta evento) {
        entidades.persist(EventoPendienteJpa.desde(evento));
    }
}
```

Fíjate en `guardar`: al existir la cuenta, no hay ninguna llamada que "guarde". La fila que devolvió `find` está bajo el control del contexto de persistencia; basta modificarla. Es lo que más confunde al principio y lo que más errores silenciosos produce cuando se modifica una entidad sin querer.

### La unidad de trabajo

<!-- file: ../proyecto-jpa/src/main/java/banco/jpa/UnidadDeTrabajoJpa.java -->
```java
package banco.jpa;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.util.function.Supplier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** El puerto de transacciones, sobre el gestor de transacciones de Spring. */
@Component
public class UnidadDeTrabajoJpa implements UnidadDeTrabajo {

    private final TransactionTemplate transacciones;

    public UnidadDeTrabajoJpa(PlatformTransactionManager gestor) {
        this.transacciones = new TransactionTemplate(gestor);
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        try {
            // Abre la transacción (o se une a la que haya), ejecuta, y confirma. Si sale una excepción, revierte.
            return transacciones.execute(estado -> trabajo.get());
        } catch (OptimisticLockingFailureException conflicto) {
            // El UPDATE con "AND version = ?" no encontró la fila. Salta al confirmar, por eso se traduce aquí.
            throw new ConflictoDeConcurrencia("una cuenta");
        }
    }
}
```

El caso de uso recibe `ConflictoDeConcurrencia`, la misma excepción que lanzaba el adaptador de memoria, y reintenta. No sabe que debajo hay Hibernate.

### La raíz de composición

<!-- file: ../proyecto-jpa/src/main/java/banco/jpa/Configuracion.java -->
```java
package banco.jpa;

import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** La raíz de composición. El núcleo no lleva ninguna anotación de Spring: se arma aquí. */
@Configuration
public class Configuracion {

    @Bean
    public Reloj reloj() {
        return Instant::now;
    }

    @Bean
    public ServicioDeCuentas servicioDeCuentas(AlmacenJpa almacen, UnidadDeTrabajo unidadDeTrabajo, Reloj reloj) {
        return new ServicioDeCuentas(almacen, almacen, almacen, unidadDeTrabajo, reloj, almacen);
    }
}
```

Compárala con el `Main` de la sesión 06: la misma línea `new ServicioDeCuentas(…)`, con otros adaptadores.

<!-- file: ../proyecto-jpa/src/main/java/banco/jpa/AplicacionBanco.java -->
```java
package banco.jpa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AplicacionBanco {

    public static void main(String[] args) {
        SpringApplication.run(AplicacionBanco.class, args);
    }
}
```

### El adaptador de entrada

<!-- file: ../proyecto-jpa/src/main/java/banco/jpa/ControladorDeCuentas.java -->
```java
package banco.jpa;

import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.entrada.AbrirCuenta;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.puertos.entrada.Depositar;
import banco.aplicacion.puertos.entrada.TransferirDinero;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Traduce HTTP y JSON a los puertos de entrada. Los mismos puertos que usaban la consola y el servidor del JDK. */
@RestController
public class ControladorDeCuentas {

    public record Apertura(String titular, long centavos) {
    }

    public record Deposito(long centavos) {
    }

    public record Transferencia(String origen, String destino, long centavos) {
    }

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
    public ResponseEntity<VistaDeCuenta> abrir(@RequestBody Apertura apertura) {
        return ResponseEntity.status(HttpStatus.CREATED).body(abrirCuenta.abrir(apertura.titular(), apertura.centavos()));
    }

    @PostMapping("/cuentas/{id}/depositos")
    public VistaDeCuenta depositar(@PathVariable String id, @RequestBody Deposito deposito) {
        return depositar.depositar(id, deposito.centavos());
    }

    @PostMapping("/transferencias")
    public ResponseEntity<Map<String, String>> transferir(@RequestHeader("Idempotency-Key") String clave,
                                                          @RequestBody Transferencia transferencia) {
        Resultado resultado = transferirDinero.transferir(
                new Orden(clave, transferencia.origen(), transferencia.destino(), transferencia.centavos()));
        HttpStatus estado = resultado == Resultado.REALIZADA ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(Map.of("resultado", resultado.name()));
    }

    @GetMapping("/cuentas/{id}")
    public VistaDeCuenta consultar(@PathVariable String id) {
        return consultarCuenta.porId(id).orElseThrow(() -> new CuentaNoEncontrada(id));
    }
}
```

<!-- file: ../proyecto-jpa/src/main/java/banco/jpa/TraductorDeErrores.java -->
```java
package banco.jpa;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.CuentaNoEncontrada;
import banco.dominio.ReglaDeNegocioViolada;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** La tabla de traducción de la sesión 06, ahora en un solo sitio. */
@RestControllerAdvice
public class TraductorDeErrores {

    @ExceptionHandler(ReglaDeNegocioViolada.class)
    public ProblemDetail reglaViolada(ReglaDeNegocioViolada rechazo) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), rechazo.getMessage());
    }

    @ExceptionHandler(CuentaNoEncontrada.class)
    public ProblemDetail noEncontrada(CuentaNoEncontrada ausente) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(404), ausente.getMessage());
    }

    @ExceptionHandler(ConflictoDeConcurrencia.class)
    public ProblemDetail conflicto(ConflictoDeConcurrencia conflicto) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(409), conflicto.getMessage());
    }
}
```

### Ejecutarlo

Desde `hexagonal/proyecto-jpa/`, con Docker y Maven:

```bash
docker compose up -d
mvn spring-boot:run

curl -s -X POST localhost:8080/cuentas -H 'Content-Type: application/json' -d '{"titular":"Ana","centavos":100000000}'
curl -s -X POST localhost:8080/cuentas -H 'Content-Type: application/json' -d '{"titular":"Luis","centavos":20000000}'
# usa los id que devolvieron las dos llamadas anteriores
curl -s -X POST localhost:8080/transferencias -H 'Content-Type: application/json' -H 'Idempotency-Key: orden-1' \
  -d '{"origen":"<id de Ana>","destino":"<id de Luis>","centavos":30000000}'
```

Con el registro de SQL activado, mira en la consola del servidor las sentencias de una transferencia y busca tres cosas: el `insert … on conflict do nothing`, los dos `update cuenta … where id=? and version=?` y en qué momento salen respecto a tu código.

### Lo que cambió y lo que no

| | Cambió |
|---|---|
| `banco.dominio` | Nada |
| `banco.aplicacion` y sus puertos | Nada |
| Adaptadores de salida | Nuevos: `AlmacenJpa`, `UnidadDeTrabajoJpa` |
| Adaptador de entrada | Nuevo: `ControladorDeCuentas` |
| Raíz de composición | Nueva: `Configuracion` |
| Pruebas del dominio y de la aplicación | Siguen pasando sin tocar nada |

Esa tabla es el argumento entero de la arquitectura hexagonal.

### Con bloqueo pesimista

Si las mismas cuentas se disputan mucho, el adaptador puede bloquear al leer. Es un cambio dentro del adaptador; el puerto no se entera. Fragmento ilustrativo:

```java
CuentaJpa fila = entidades.find(CuentaJpa.class, id, LockModeType.PESSIMISTIC_WRITE);   // SELECT … FOR UPDATE
```

Con dos cuentas por transferencia aparece el riesgo de interbloqueo de la sesión 05: habría que leerlas siempre en el mismo orden.

## Errores comunes

- Entidades JPA usadas como modelo de dominio, como cuerpo de la API y como fila, todo a la vez.
- Devolver entidades JPA desde el controlador: relaciones perezosas que fallan al serializar y un esquema que se filtra a la API.
- Dejar `open-in-view` activado y hacer consultas desde la capa de presentación sin saberlo.
- Dejar que Hibernate genere el esquema en producción.
- Creer que `@Version` reintenta.
- Capturar la excepción de bloqueo optimista dentro de la transacción: salta al confirmar, fuera de tu `try`.
- Modificar una entidad gestionada "solo para calcular algo" y encontrarse un `UPDATE` que nadie pidió.
- Recorrer una relación perezosa en un bucle: una consulta por elemento.
- `@Enumerated` sin `EnumType.STRING`: se guarda la posición y reordenar el enum corrompe los datos.
- `@Transactional` repartido por repositorios y controladores, sin una frontera clara.

## Taller

**Objetivo:** persistir tu servicio de pedidos en PostgreSQL con JPA, sin modificar el dominio ni la aplicación.

Sobre tu servicio de la sesión 06. Necesitas Maven y Docker.

1. Crea un proyecto Spring Boot con JPA y PostgreSQL que compile tu núcleo sin copiarlo.
2. Diseña el esquema a mano: pedidos, líneas de pedido, existencias, claves de idempotencia y bandeja de salida. Decide qué restricciones repiten reglas del dominio y por qué.
3. Decide si usas un modelo o dos. Escribe la decisión y lo que te cuesta. Si eliges uno, enumera qué tuviste que cambiar en tu dominio para que JPA lo acepte.
4. Escribe las entidades de persistencia y la traducción desde y hacia el dominio. Un pedido tiene líneas: decide cómo se mapea esa relación y cómo se cargan.
5. Escribe los adaptadores de salida y la unidad de trabajo. El conflicto de versión debe llegar al caso de uso como tu excepción de la sesión 05.
6. Escribe la raíz de composición sin añadir ninguna anotación de Spring al núcleo.
7. Activa el registro de SQL, crea un pedido con dos líneas y copia las sentencias que se ejecutaron. Explica cada una y en qué momento salió.
8. Consulta un pedido con sus líneas y cuenta las consultas. Luego lista diez pedidos con sus líneas y vuelve a contar. Si el número crece con los pedidos, corrígelo.
9. Lanza dos peticiones simultáneas por la última unidad de un producto, por ejemplo con dos `curl` en paralelo o un script. Comprueba en la base que se vendió una sola.
10. Ejecuta las pruebas del dominio y de la aplicación de las sesiones anteriores. No debiste tocar ninguna.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| El dominio y la aplicación no tienen cambios | `git diff` de esos paquetes |
| El núcleo no importa nada de `jakarta` ni de `org.springframework` | Búsqueda de `import` |
| El esquema está escrito a mano y Hibernate no lo genera | Configuración |
| Dos peticiones por la última unidad venden una sola | El paso 9 |
| Listar pedidos con líneas no genera una consulta por pedido | El paso 8 |
| Las pruebas anteriores pasan sin modificarse | Ejecución |

**Entrega:** rama `taller/hexagonal-07`, con el proyecto en `taller/solucion/` y `ANALISIS.md` con la decisión del paso 3, el SQL del paso 7 y los conteos del paso 8.

## En el trabajo real

Lo habitual es Spring Data JPA: una interfaz que extiende `JpaRepository` y te ahorra el `EntityManager`. Encaja igual en la arquitectura: la interfaz de Spring Data es un detalle **dentro** del adaptador, y el adaptador sigue implementando tu puerto. Lo que hay que saber es que `save` decide entre insertar y fusionar según considere que la entidad es nueva, y con identificadores asignados por ti esa decisión no siempre es la que esperas.

El esquema se versiona con migraciones (Flyway o Liquibase), no con un `schema.sql` que se ejecuta al arrancar. Y el adaptador de persistencia se prueba contra una base real, normalmente con Testcontainers: una base en memoria distinta de la de producción no prueba ni los bloqueos ni el SQL nativo.

El tamaño del pool de conexiones limita cuántas transacciones pueden estar abiertas a la vez. Está en la ruta de [HikariCP](../../hikaricp/).

## Preguntas senior

1. ¿En qué capa vive el ORM en una arquitectura hexagonal y por qué?
2. ¿Qué ganas y qué pagas al separar las entidades de persistencia del dominio?
3. ¿Por qué el conflicto de versión aparece al confirmar y no al modificar la entidad?
4. ¿Qué es el contexto de persistencia y qué tiene que ver con una unidad de trabajo?
5. ¿Por qué la clave de idempotencia se inserta con SQL nativo?
6. ¿Qué problema produce `open-in-view` activado?
7. ¿Cómo detectas y corriges un N+1?
