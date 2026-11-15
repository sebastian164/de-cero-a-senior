# 03 — Puertos y casos de uso

## Idea

La ventanilla de un banco y su trastienda. El cliente no entra a la bóveda ni toca el libro de cuentas: se acerca a una ventanilla que ofrece operaciones concretas, "consignar", "transferir". Detrás, el cajero tampoco sabe en qué edificio está la bóveda ni qué empresa lleva el correo: tiene un compañero que "guarda" y otro que "avisa". Si mañana cambian de empresa de mensajería, el cajero no se entera. Las ventanillas son los puertos de entrada; los compañeros, los de salida.

## Lo esencial

### Caso de uso

Una operación completa que el sistema ofrece al exterior: "abrir una cuenta", "transferir dinero". Su trabajo es **coordinar**, no decidir reglas:

1. Trae lo que necesita, por puertos de salida.
2. Le pide al dominio que aplique las reglas.
3. Guarda el resultado y avisa, por puertos de salida.

Si un caso de uso tiene un `if` con una regla del negocio, esa regla está en el sitio equivocado.

### Puertos de entrada

Una interfaz por operación, con el vocabulario del negocio. Reciben y devuelven datos simples, no objetos del dominio: así el exterior no puede manipular una `Cuenta` saltándose los casos de uso.

### Puertos de salida

Lo que la aplicación **necesita** del mundo, dicho en sus propios términos. La pregunta para diseñarlos no es "qué ofrece la base de datos" sino "qué necesita el caso de uso".

| Puerto | Qué necesita la aplicación | Lo que **no** dice |
|---|---|---|
| `RepositorioDeCuentas` | Guardar y encontrar cuentas | Si es SQL, un archivo o memoria |
| `RegistroDeMovimientos` | Anotar y consultar renglones del extracto | Dónde se guardan |
| `OrdenesProcesadas` | Saber si una orden ya se atendió | Cómo se recuerda |
| `UnidadDeTrabajo` | Que un grupo de cambios ocurra completo o no ocurra | Cómo se logra |
| `Reloj` | Saber qué hora es | De dónde sale |
| `PublicadorDeEventos` | Avisar de lo que pasó | Si es Kafka, un correo o un registro |

### Granularidad

| Demasiado fino | Demasiado grueso | Razonable |
|---|---|---|
| Un puerto por método: `BuscadorDeCuentaPorId`, `GuardadorDeCuenta` | Un `Persistencia` con cuarenta métodos | Un puerto por necesidad coherente |

## El código

### Puertos de entrada

<!-- file: ../proyecto/src/banco/aplicacion/puertos/entrada/AbrirCuenta.java -->
```java
package banco.aplicacion.puertos.entrada;

import banco.aplicacion.VistaDeCuenta;

public interface AbrirCuenta {

    VistaDeCuenta abrir(String titular, long saldoInicialCentavos);
}
```

<!-- file: ../proyecto/src/banco/aplicacion/puertos/entrada/Depositar.java -->
```java
package banco.aplicacion.puertos.entrada;

import banco.aplicacion.VistaDeCuenta;

public interface Depositar {

    VistaDeCuenta depositar(String cuentaId, long centavos);
}
```

<!-- file: ../proyecto/src/banco/aplicacion/puertos/entrada/TransferirDinero.java -->
```java
package banco.aplicacion.puertos.entrada;

public interface TransferirDinero {

    /** La clave identifica la orden: reenviar la misma orden no transfiere dos veces. */
    record Orden(String clave, String origenId, String destinoId, long centavos) {
    }

    enum Resultado {
        REALIZADA,
        REPETIDA
    }

    Resultado transferir(Orden orden);
}
```

<!-- file: ../proyecto/src/banco/aplicacion/puertos/entrada/ConsultarCuenta.java -->
```java
package banco.aplicacion.puertos.entrada;

import banco.aplicacion.VistaDeCuenta;
import banco.dominio.Movimiento;
import java.util.List;
import java.util.Optional;

public interface ConsultarCuenta {

    Optional<VistaDeCuenta> porId(String cuentaId);

    List<Movimiento> movimientosDe(String cuentaId);
}
```

### Puertos de salida

<!-- file: ../proyecto/src/banco/aplicacion/puertos/salida/RepositorioDeCuentas.java -->
```java
package banco.aplicacion.puertos.salida;

import banco.dominio.Cuenta;
import java.util.Optional;

public interface RepositorioDeCuentas {

    String siguienteId();

    /** Lanza ConflictoDeConcurrencia si otro cambió la cuenta desde que se leyó. */
    void guardar(Cuenta cuenta);

    Optional<Cuenta> buscar(String id);
}
```

<!-- file: ../proyecto/src/banco/aplicacion/puertos/salida/RegistroDeMovimientos.java -->
```java
package banco.aplicacion.puertos.salida;

import banco.dominio.Movimiento;
import java.util.List;

public interface RegistroDeMovimientos {

    void registrar(Movimiento movimiento);

    List<Movimiento> de(String cuentaId);
}
```

<!-- file: ../proyecto/src/banco/aplicacion/puertos/salida/OrdenesProcesadas.java -->
```java
package banco.aplicacion.puertos.salida;

public interface OrdenesProcesadas {

    /** Devuelve falso si la clave ya estaba registrada. */
    boolean registrarSiEsNueva(String clave);
}
```

<!-- file: ../proyecto/src/banco/aplicacion/puertos/salida/UnidadDeTrabajo.java -->
```java
package banco.aplicacion.puertos.salida;

import java.util.function.Supplier;

/** Todo lo que ocurra dentro se confirma junto, o no ocurre. Es la sesión 04. */
public interface UnidadDeTrabajo {

    <T> T ejecutar(Supplier<T> trabajo);
}
```

<!-- file: ../proyecto/src/banco/aplicacion/puertos/salida/Reloj.java -->
```java
package banco.aplicacion.puertos.salida;

import java.time.Instant;

public interface Reloj {

    Instant ahora();
}
```

<!-- file: ../proyecto/src/banco/aplicacion/puertos/salida/PublicadorDeEventos.java -->
```java
package banco.aplicacion.puertos.salida;

import banco.aplicacion.EventoDeCuenta;

public interface PublicadorDeEventos {

    void publicar(EventoDeCuenta evento);
}
```

### La aplicación

<!-- file: ../proyecto/src/banco/aplicacion/VistaDeCuenta.java -->
```java
package banco.aplicacion;

import banco.dominio.Cuenta;

/** Lo que el exterior ve de una cuenta. No es la cuenta: no tiene comportamiento. */
public record VistaDeCuenta(String id, String titular, long saldoCentavos) {

    public static VistaDeCuenta de(Cuenta cuenta) {
        return new VistaDeCuenta(cuenta.id(), cuenta.titular(), cuenta.saldo().centavos());
    }
}
```

<!-- file: ../proyecto/src/banco/aplicacion/EventoDeCuenta.java -->
```java
package banco.aplicacion;

import java.time.Instant;

/** Un hecho que ya ocurrió. El id es estable: quien lo reciba dos veces puede descartar la repetición. */
public record EventoDeCuenta(String id, String tipo, String cuentaId, long centavos, Instant ocurridoEn) {
}
```

<!-- file: ../proyecto/src/banco/aplicacion/CuentaNoEncontrada.java -->
```java
package banco.aplicacion;

public class CuentaNoEncontrada extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CuentaNoEncontrada(String cuentaId) {
        super("no existe la cuenta " + cuentaId);
    }
}
```

<!-- file: ../proyecto/src/banco/aplicacion/ConflictoDeConcurrencia.java -->
```java
package banco.aplicacion;

/** Otro cambió lo mismo entre que se leyó y se intentó guardar. */
public class ConflictoDeConcurrencia extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictoDeConcurrencia(String recurso) {
        super(recurso + " cambió mientras se trabajaba con ella");
    }
}
```

<!-- file: ../proyecto/src/banco/aplicacion/ServicioDeCuentas.java -->
```java
package banco.aplicacion;

import banco.aplicacion.puertos.entrada.AbrirCuenta;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.puertos.entrada.Depositar;
import banco.aplicacion.puertos.entrada.TransferirDinero;
import banco.aplicacion.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import banco.dominio.Transferencia;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Los casos de uso del banco. Coordina; las reglas están en el dominio. Solo conoce puertos. */
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
    public VistaDeCuenta abrir(String titular, long saldoInicialCentavos) {
        return unidadDeTrabajo.ejecutar(() -> {
            Instant ahora = reloj.ahora();
            Cuenta cuenta = Cuenta.abrir(cuentas.siguienteId(), titular, new Dinero(saldoInicialCentavos), dia(ahora));
            cuentas.guardar(cuenta);
            if (saldoInicialCentavos > 0) {
                movimientos.registrar(new Movimiento(cuenta.id(), Movimiento.Tipo.DEPOSITO, cuenta.saldo(), ahora, "apertura"));
            }
            return VistaDeCuenta.de(cuenta);
        });
    }

    @Override
    public VistaDeCuenta depositar(String cuentaId, long centavos) {
        return conReintento(() -> unidadDeTrabajo.ejecutar(() -> {
            Instant ahora = reloj.ahora();
            Cuenta cuenta = exigir(cuentaId);
            Dinero valor = new Dinero(centavos);
            cuenta.depositar(valor);
            cuentas.guardar(cuenta);
            movimientos.registrar(new Movimiento(cuentaId, Movimiento.Tipo.DEPOSITO, valor, ahora, "depósito"));
            return VistaDeCuenta.de(cuenta);
        }));
    }

    @Override
    public Resultado transferir(Orden orden) {
        return conReintento(() -> unidadDeTrabajo.ejecutar(() -> {         // todo o nada (sesión 04)
            if (!ordenes.registrarSiEsNueva(orden.clave())) {
                return Resultado.REPETIDA;                                 // la misma orden, otra vez: no se repite
            }
            Instant ahora = reloj.ahora();
            Cuenta origen = exigir(orden.origenId());
            Cuenta destino = exigir(orden.destinoId());
            Dinero valor = new Dinero(orden.centavos());

            Transferencia.ejecutar(origen, destino, valor, dia(ahora));    // las reglas, en el dominio

            cuentas.guardar(origen);
            cuentas.guardar(destino);
            movimientos.registrar(new Movimiento(origen.id(), Movimiento.Tipo.TRANSFERENCIA_ENVIADA, valor, ahora, orden.clave()));
            movimientos.registrar(new Movimiento(destino.id(), Movimiento.Tipo.TRANSFERENCIA_RECIBIDA, valor, ahora, orden.clave()));
            eventos.publicar(new EventoDeCuenta("transferencia:" + orden.clave(), "TransferenciaRealizada",
                    origen.id(), orden.centavos(), ahora));
            return Resultado.REALIZADA;
        }));
    }

    @Override
    public Optional<VistaDeCuenta> porId(String cuentaId) {
        return cuentas.buscar(cuentaId).map(VistaDeCuenta::de);
    }

    @Override
    public List<Movimiento> movimientosDe(String cuentaId) {
        return movimientos.de(cuentaId);
    }

    private Cuenta exigir(String cuentaId) {
        return cuentas.buscar(cuentaId).orElseThrow(() -> new CuentaNoEncontrada(cuentaId));
    }

    private static LocalDate dia(Instant instante) {
        return LocalDate.ofInstant(instante, ZoneOffset.UTC);
    }

    /** Si otro cambió la cuenta a la vez, se vuelve a intentar desde el principio (sesión 05). */
    private <T> T conReintento(Supplier<T> operacion) {
        for (int intento = 1; ; intento++) {
            try {
                return operacion.get();
            } catch (ConflictoDeConcurrencia conflicto) {
                if (intento == INTENTOS) {
                    throw conflicto;
                }
            }
        }
    }
}
```

Lee `transferir` de arriba abajo: es la descripción de la operación en el lenguaje del negocio. No hay una sola mención a HTTP, a SQL ni a un archivo. Compáralo con el `procesar` de la sesión 01.

Dos envolturas quedan pendientes de explicar: `unidadDeTrabajo.ejecutar` (sesión 04) y `conReintento` (sesión 05).

### El primer adaptador: memoria

El primer adaptador que se escribe casi nunca es el de producción: es el que permite probar el caso de uso. Este implementa cinco puertos a la vez, porque representa una sola "base de datos".

<!-- file: ../proyecto/src/banco/adaptadores/salida/memoria/AlmacenEnMemoria.java -->
```java
package banco.adaptadores.salida.memoria;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import banco.dominio.Cuenta;
import banco.dominio.Movimiento;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Una "base de datos" en memoria, con transacciones y control de versión. */
public final class AlmacenEnMemoria implements RepositorioDeCuentas, RegistroDeMovimientos, OrdenesProcesadas,
        PublicadorDeEventos, UnidadDeTrabajo {

    /** Lo confirmado: lo que ve todo el mundo. */
    private final Map<String, Cuenta> cuentas = new LinkedHashMap<>();
    private final List<Movimiento> movimientos = new ArrayList<>();
    private final Set<String> ordenes = new LinkedHashSet<>();
    private final List<EventoDeCuenta> bandejaDeSalida = new ArrayList<>();
    private final AtomicInteger secuencia = new AtomicInteger();

    /** Lo pendiente de una transacción: solo lo ve quien la abrió. */
    private static final class Transaccion {
        private final Map<String, Cuenta> cuentas = new LinkedHashMap<>();
        private final List<Movimiento> movimientos = new ArrayList<>();
        private final Set<String> ordenes = new LinkedHashSet<>();
        private final List<EventoDeCuenta> eventos = new ArrayList<>();
    }

    private final ThreadLocal<Transaccion> abierta = new ThreadLocal<>();

    // ---- UnidadDeTrabajo (sesión 04) ----

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        if (abierta.get() != null) {
            return trabajo.get();                                // ya hay una abierta: se une a ella
        }
        Transaccion transaccion = new Transaccion();
        abierta.set(transaccion);
        try {
            T resultado = trabajo.get();
            confirmar(transaccion);                              // todo lo pendiente pasa a ser visible, de una vez
            return resultado;
        } finally {
            abierta.remove();                                    // si algo falló, lo pendiente se descarta: eso es revertir
        }
    }

    private synchronized void confirmar(Transaccion transaccion) {
        for (Cuenta cuenta : transaccion.cuentas.values()) {
            exigirVersionVigente(cuenta);
        }
        for (String clave : transaccion.ordenes) {
            if (ordenes.contains(clave)) {
                throw new ConflictoDeConcurrencia("la orden " + clave);
            }
        }
        for (Cuenta cuenta : transaccion.cuentas.values()) {
            cuentas.put(cuenta.id(), copia(cuenta, cuenta.version() + 1));
        }
        movimientos.addAll(transaccion.movimientos);
        ordenes.addAll(transaccion.ordenes);
        bandejaDeSalida.addAll(transaccion.eventos);
    }

    /** Fuera de una transacción, cada operación es una transacción de un solo paso. */
    private void enTransaccion(Consumer<Transaccion> accion) {
        ejecutar(() -> {
            accion.accept(abierta.get());
            return null;
        });
    }

    // ---- RepositorioDeCuentas ----

    @Override
    public String siguienteId() {
        return "cuenta-" + secuencia.incrementAndGet();          // como una secuencia de base de datos: no se revierte
    }

    @Override
    public void guardar(Cuenta cuenta) {
        enTransaccion(transaccion -> {
            synchronized (this) {
                exigirVersionVigente(cuenta);                    // sesión 05
            }
            transaccion.cuentas.put(cuenta.id(), copia(cuenta, cuenta.version()));
        });
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        Transaccion transaccion = abierta.get();
        if (transaccion != null && transaccion.cuentas.containsKey(id)) {
            Cuenta pendiente = transaccion.cuentas.get(id);      // quien la cambió ve su propio cambio
            return Optional.of(copia(pendiente, pendiente.version()));
        }
        synchronized (this) {
            return Optional.ofNullable(cuentas.get(id)).map(cuenta -> copia(cuenta, cuenta.version()));
        }
    }

    private void exigirVersionVigente(Cuenta cuenta) {
        Cuenta confirmada = cuentas.get(cuenta.id());
        if (confirmada != null && confirmada.version() != cuenta.version()) {
            throw new ConflictoDeConcurrencia("la cuenta " + cuenta.id());   // alguien la cambió desde que se leyó
        }
    }

    /** Se entregan y se guardan copias: quien tiene una cuenta en la mano no modifica lo guardado. */
    private static Cuenta copia(Cuenta cuenta, long version) {
        return Cuenta.existente(cuenta.id(), cuenta.titular(), cuenta.saldo(), cuenta.enviadoHoy(), cuenta.diaDelTope(), version);
    }

    // ---- RegistroDeMovimientos ----

    @Override
    public void registrar(Movimiento movimiento) {
        enTransaccion(transaccion -> transaccion.movimientos.add(movimiento));
    }

    @Override
    public List<Movimiento> de(String cuentaId) {
        List<Movimiento> resultado = new ArrayList<>();
        synchronized (this) {
            resultado.addAll(movimientos);
        }
        Transaccion transaccion = abierta.get();
        if (transaccion != null) {
            resultado.addAll(transaccion.movimientos);
        }
        resultado.removeIf(movimiento -> !movimiento.cuentaId().equals(cuentaId));
        return resultado;
    }

    // ---- OrdenesProcesadas ----

    @Override
    public boolean registrarSiEsNueva(String clave) {
        boolean[] nueva = new boolean[1];
        enTransaccion(transaccion -> {
            boolean confirmada;
            synchronized (this) {
                confirmada = ordenes.contains(clave);
            }
            nueva[0] = !confirmada && transaccion.ordenes.add(clave);
        });
        return nueva[0];
    }

    // ---- PublicadorDeEventos: una bandeja de salida (sesión 04) ----

    @Override
    public void publicar(EventoDeCuenta evento) {
        enTransaccion(transaccion -> transaccion.eventos.add(evento));
    }

    /** Entrega lo confirmado a su destino real. Si el destino falla, el evento se queda para el siguiente intento. */
    public synchronized int entregarPendientes(Consumer<EventoDeCuenta> destino) {
        int entregados = 0;
        for (Iterator<EventoDeCuenta> pendientes = bandejaDeSalida.iterator(); pendientes.hasNext(); ) {
            destino.accept(pendientes.next());
            pendientes.remove();
            entregados++;
        }
        return entregados;
    }
}
```

Hay tres cosas en este adaptador que todavía no se han explicado, y cada una tiene su sesión:

| En el código | Qué es | Sesión |
|---|---|---|
| `Transaccion`, `ejecutar`, `confirmar` | Los cambios quedan pendientes y se confirman todos juntos, o se descartan | 04 |
| `publicar` y `entregarPendientes` | Los eventos se guardan con la transacción y se entregan después | 04 |
| `exigirVersionVigente` | Detecta que otro cambió la cuenta mientras tanto | 05 |

Por ahora basta con esto: guarda y encuentra cosas, y cumple los puertos.

### Las pruebas del caso de uso

Sin servidor, sin base de datos: el servicio real, conectado a adaptadores de memoria y a un reloj fijo.

<!-- file: ../proyecto/pruebas/banco/PruebasDeLaAplicacion.java -->
```java
package banco;

import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.aplicacion.puertos.salida.Reloj;
import banco.dominio.ReglaDeNegocioViolada;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Los casos de uso, con adaptadores de memoria. */
public final class PruebasDeLaAplicacion {

    private PruebasDeLaAplicacion() {
    }

    public static void ejecutar() {
        System.out.println("Aplicación:");

        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        List<EventoDeCuenta> eventos = new ArrayList<>();
        Reloj reloj = () -> Instant.parse("2026-11-15T10:00:00Z");
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, almacen, reloj, eventos::add);

        VistaDeCuenta ana = banco.abrir("Ana", 100_000_000);
        VistaDeCuenta luis = banco.abrir("Luis", 20_000_000);

        Resultado resultado = banco.transferir(new Orden("orden-1", ana.id(), luis.id(), 30_000_000));
        Comprobar.igual("la transferencia se realiza", Resultado.REALIZADA, resultado);
        Comprobar.igual("el origen queda con menos", 70_000_000L, saldo(banco, ana.id()));
        Comprobar.igual("el destino queda con más", 50_000_000L, saldo(banco, luis.id()));
        Comprobar.igual("el origen tiene apertura y envío en su extracto", 2, banco.movimientosDe(ana.id()).size());
        Comprobar.igual("se publica un evento", "TransferenciaRealizada", eventos.get(0).tipo());

        Comprobar.lanza("transferir a una cuenta que no existe falla", CuentaNoEncontrada.class,
                () -> banco.transferir(new Orden("orden-2", ana.id(), "cuenta-99", 1_000)));
        Comprobar.lanza("la regla de saldo llega desde el dominio", ReglaDeNegocioViolada.class,
                () -> banco.transferir(new Orden("orden-3", luis.id(), ana.id(), 50_000_001)));
        Comprobar.igual("una transferencia fallida no deja rastro", 70_000_000L, saldo(banco, ana.id()));
        Comprobar.igual("ni publica eventos", 1, eventos.size());

        Comprobar.igual("depositar suma al saldo", 55_000_000L, banco.depositar(luis.id(), 5_000_000).saldoCentavos());
    }

    private static long saldo(ServicioDeCuentas banco, String cuentaId) {
        return banco.porId(cuentaId).orElseThrow().saldoCentavos();
    }
}
```

## Errores comunes

- Casos de uso con reglas del negocio dentro. Si hay un `if` sobre el saldo en el servicio, falta en el dominio.
- Puertos de salida que copian la tecnología: `ejecutarSql(String)`, `enviarAKafka(String tema, …)`.
- Puertos de entrada que reciben tipos de HTTP o devuelven entidades del dominio que el exterior puede modificar.
- Un puerto por cada método, o uno solo para todo.
- Un caso de uso que llama a otro caso de uso por comodidad y acaba con transacciones anidadas que nadie entiende.
- Escribir primero el adaptador de la base de datos y diseñar el puerto a su imagen.

## Taller

**Objetivo:** escribir los casos de uso y los puertos del servicio de pedidos, y probarlos con adaptadores de memoria.

Sobre tu dominio de la sesión 02.

1. Lista las operaciones que el servicio ofrece. Como mínimo: crear un pedido, confirmarlo, cancelarlo y consultarlo. Define un puerto de entrada para cada una, con datos simples de entrada y de salida.
2. Para cada caso de uso, escribe en una frase qué necesita del exterior. De esas frases salen tus puertos de salida. Como mínimo: guardar y encontrar pedidos, consultar precios, consultar y descontar existencias, saber la hora y avisar de lo ocurrido.
3. Precios y existencias, ¿son un puerto o dos? ¿Y si mañana los precios vienen de otro servicio y las existencias de una base propia? Decide y justifica.
4. Escribe el servicio de aplicación. Revisa cada `if`: los que comparan valores del negocio van al dominio.
5. Escribe adaptadores de memoria para todos los puertos de salida.
6. Prueba cada caso de uso con el servicio real y los adaptadores de memoria: caminos felices y cada rechazo.
7. Revisa tus puertos de entrada: ¿algún método devuelve un objeto del dominio que el llamador podría modificar? Cámbialo por una vista.
8. Añade un caso de uso que no estaba previsto: "listar los pedidos de un cliente". ¿Cuántos archivos tocaste y de qué capa?
9. Deja pendiente, con un comentario, la pregunta que no puedes responder todavía: si crear el pedido descuenta existencias y luego falla al guardar, ¿qué pasa? Es la sesión siguiente.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| La aplicación no importa nada de adaptadores | Búsqueda de `import` |
| Los puertos de salida no nombran ninguna tecnología | Lectura |
| Cada caso de uso tiene pruebas con adaptadores de memoria | Las pruebas |
| No quedan reglas del negocio en el servicio | Revisión de cada `if` |

**Entrega:** rama `taller/hexagonal-03`, con el proyecto en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

En Spring, el servicio de aplicación es un `@Service`, los puertos de salida son interfaces que implementan clases anotadas con `@Repository` o `@Component`, y los puertos de entrada los llama un `@RestController`. La estructura es la misma; lo que cambia es quién conecta las piezas. Eso es la sesión 06.

## Preguntas senior

1. ¿Qué responsabilidad tiene un caso de uso y cuál no?
2. ¿Cómo decides la forma de un puerto de salida?
3. ¿Por qué los puertos de entrada no devuelven entidades del dominio?
4. ¿Qué problema tiene un puerto llamado `ejecutarConsulta(String sql)`?
5. ¿Por qué el primer adaptador suele ser uno de memoria?
