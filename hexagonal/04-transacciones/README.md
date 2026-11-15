# 04 — Transacciones

## Idea

Cambiar un billete de cien por dos de cincuenta. Hay un instante en que ya entregaste el de cien y todavía no recibiste nada. Si en ese instante se va la luz y cada uno se va por su lado, perdiste cien. Lo que quieres es que el cambio ocurra **entero o no ocurra**: o tienes los dos de cincuenta, o sigues con tu billete. Nunca el estado intermedio.

Una transferencia es exactamente eso: sale de una cuenta y entra en otra. Son dos escrituras, y entre una y otra el dinero no está en ninguna parte.

## Lo esencial

### Qué es una transacción

Un grupo de operaciones que el sistema trata como **una sola**. Termina de una de dos formas:

| Final | Nombre | Efecto |
|---|---|---|
| Todo salió bien | Confirmar (*commit*) | Todos los cambios quedan guardados y visibles |
| Algo falló | Revertir (*rollback*) | No queda ninguno, como si nunca se hubiera empezado |

### ACID, con el banco

| Letra | Propiedad | Qué promete | En una transferencia |
|---|---|---|---|
| **A** | Atomicidad | Todo o nada | No existe "salió de Ana y no llegó a Luis" |
| **C** | Consistencia | Las reglas se cumplen antes y después | Ningún saldo negativo; el dinero total no cambia |
| **I** | Aislamiento | Las transacciones simultáneas no se estorban | Dos transferencias a la vez no pisan el saldo una de otra |
| **D** | Durabilidad | Lo confirmado sobrevive a un apagón | Si el banco dijo "hecho", sigue hecho al reiniciar |

Dos aclaraciones que evitan confusiones:

- La **consistencia** es responsabilidad compartida. La base de datos garantiza lo que le declaraste (claves, restricciones); las reglas del negocio las garantiza tu dominio. La transacción solo asegura que, si el dominio rechaza algo, no queda nada a medias.
- El **aislamiento** no es un sí o un no: tiene niveles. Es la sesión 05 completa.

### Dónde empieza y dónde termina

**La frontera de la transacción es el caso de uso.** Una operación del negocio, una transacción.

| Frontera | Problema |
|---|---|
| Cada llamada al repositorio | No hay atomicidad: es lo mismo que no tener transacción |
| El caso de uso | Lo correcto: la operación del negocio es la unidad |
| La petición HTTP entera | Demasiado larga: incluye serializar, validar formato y, a veces, llamar a otros servicios |

### Por qué es un puerto

El caso de uso necesita decir "esto va junto". No necesita saber cómo se logra. Por eso existe `UnidadDeTrabajo`:

```java
return unidadDeTrabajo.ejecutar(() -> {
    // todo lo que ocurra aquí se confirma junto, o no ocurre
});
```

| Adaptador | Cómo cumple |
|---|---|
| Memoria (este proyecto) | Guarda los cambios aparte y los aplica todos al final |
| JDBC | `setAutoCommit(false)`, y al final `commit()` o `rollback()` |
| JPA con Spring (sesión 07) | Una transacción de Spring alrededor del trabajo |

El dominio no se entera de nada de esto: `Cuenta` y `Transferencia` no saben que existen las transacciones.

### Cómo lo hace el adaptador de memoria

Vuelve a `AlmacenEnMemoria` de la sesión 03. Ahora se entiende:

1. `ejecutar` abre una `Transaccion`: un cuaderno de cambios pendientes que solo ve quien la abrió.
2. `guardar`, `registrar`, `registrarSiEsNueva` y `publicar` escriben en ese cuaderno, no en los datos confirmados.
3. Si el trabajo termina bien, `confirmar` pasa todo el cuaderno a los datos confirmados de una sola vez.
4. Si el trabajo lanza una excepción, el cuaderno se descarta. Eso es revertir.

Una base de datos real hace lo mismo con más ingeniería: un registro de escritura anticipada en disco, para que lo confirmado sobreviva a un apagón.

### En SQL

La misma transferencia, vista desde la base de datos (ilustrativo):

```sql
BEGIN;
UPDATE cuenta SET saldo = saldo - 30000000 WHERE id = 'cuenta-1';
UPDATE cuenta SET saldo = saldo + 30000000 WHERE id = 'cuenta-2';
INSERT INTO movimiento (cuenta_id, tipo, valor) VALUES ('cuenta-1', 'TRANSFERENCIA_ENVIADA', 30000000);
INSERT INTO movimiento (cuenta_id, tipo, valor) VALUES ('cuenta-2', 'TRANSFERENCIA_RECIBIDA', 30000000);
COMMIT;      -- o ROLLBACK, y no pasó nada
```

### Idempotencia: la misma orden, dos veces

El cliente envía una transferencia y se le corta la conexión antes de recibir la respuesta. No sabe si se hizo. Lo razonable es reintentar. Si el banco la ejecuta otra vez, Ana pagó dos veces.

La solución: cada orden lleva una **clave** que elige quien la envía. El banco anota las claves que ya atendió.

| Detalle | Por qué |
|---|---|
| La clave la genera el cliente | Solo él sabe que dos envíos son la misma orden |
| La clave se anota **dentro de la misma transacción** | Si se anotara aparte, podría quedar anotada una orden que falló, o hecha una que no quedó anotada |
| Una orden repetida responde bien, no con error | Para el cliente, el resultado es el que quería: la transferencia está hecha |

### Lo que una transacción no cubre

Una transacción de base de datos solo abarca **esa base de datos**. No puede deshacer un correo enviado, un mensaje publicado en Kafka ni una llamada a otro servicio.

| Qué haces dentro de la transacción | Qué pasa si después se revierte |
|---|---|
| Guardar en tu base | Se deshace |
| Publicar un evento en un broker | El evento ya salió: avisaste de algo que no ocurrió |
| Llamar a otro servicio | El otro ya actuó |

Para los eventos, la salida es la **bandeja de salida**: el evento se guarda en tu propia base, en la misma transacción, y un proceso aparte lo entrega después. En `AlmacenEnMemoria`, `publicar` escribe en el cuaderno de la transacción y `entregarPendientes` hace de mensajero. El patrón completo, con Kafka, está en [Outbox transaccional](../../kafka-y-sagas/kafka/07-outbox-transaccional/).

Cuando la operación abarca varios servicios, cada uno con su base, ya no hay transacción que valga: eso son las [sagas](../../kafka-y-sagas/sagas/).

### Transacciones cortas

Mientras una transacción está abierta, retiene recursos: una conexión del pool y, a menudo, bloqueos sobre filas.

- No llames a servicios externos con la transacción abierta.
- No esperes al usuario con la transacción abierta.
- Lee lo que puedas antes y abre la transacción para lo que debe ir junto.

## El código

Dos dobles de prueba permiten provocar el problema a voluntad.

<!-- file: ../proyecto/pruebas/banco/SinTransaccion.java -->
```java
package banco;

import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.util.function.Supplier;

/** Una unidad de trabajo que no hace nada: cada escritura queda guardada por su cuenta. */
public final class SinTransaccion implements UnidadDeTrabajo {

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return trabajo.get();
    }
}
```

<!-- file: ../proyecto/pruebas/banco/RepositorioQueFalla.java -->
```java
package banco;

import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import java.util.Optional;

/** Envuelve un repositorio real y falla al guardar una cuenta concreta: simula una caída a mitad de la operación. */
public final class RepositorioQueFalla implements RepositorioDeCuentas {

    private final RepositorioDeCuentas real;
    private String cuentaQueFalla;

    public RepositorioQueFalla(RepositorioDeCuentas real) {
        this.real = real;
    }

    public void fallarAlGuardar(String cuentaId) {
        this.cuentaQueFalla = cuentaId;
    }

    public void reparar() {
        this.cuentaQueFalla = null;
    }

    @Override
    public String siguienteId() {
        return real.siguienteId();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        if (cuenta.id().equals(cuentaQueFalla)) {
            throw new IllegalStateException("se perdió la conexión al guardar " + cuenta.id());
        }
        real.guardar(cuenta);
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        return real.buscar(id);
    }
}
```

### Sin transacción y con transacción

<!-- file: ../proyecto/pruebas/banco/PruebasDeTransacciones.java -->
```java
package banco;

import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Atomicidad, idempotencia y bandeja de salida, provocando el fallo a voluntad. */
public final class PruebasDeTransacciones {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");

    private PruebasDeTransacciones() {
    }

    public static void ejecutar() {
        System.out.println("Transacciones, sin unidad de trabajo:");
        sinTransaccion();
        System.out.println("Transacciones, con unidad de trabajo:");
        conTransaccion();
        System.out.println("Bandeja de salida:");
        bandejaDeSalida();
    }

    private static void sinTransaccion() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        RepositorioQueFalla cuentas = new RepositorioQueFalla(almacen);
        UnidadDeTrabajo ninguna = new SinTransaccion();
        ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, almacen, almacen, ninguna, RELOJ, almacen);
        String ana = banco.abrir("Ana", 100_000_000).id();
        String luis = banco.abrir("Luis", 20_000_000).id();

        cuentas.fallarAlGuardar(luis);                           // el fallo ocurre entre el débito y el abono
        Comprobar.lanza("la transferencia falla a mitad", IllegalStateException.class,
                () -> banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));

        Comprobar.igual("a Ana sí le salió el dinero", 70_000_000L, saldo(banco, ana));
        Comprobar.igual("a Luis no le llegó", 20_000_000L, saldo(banco, luis));
        Comprobar.igual("faltan 300.000 pesos en el banco", 90_000_000L, saldo(banco, ana) + saldo(banco, luis));

        cuentas.reparar();
        Comprobar.igual("y reintentar no lo arregla: la orden figura como atendida", Resultado.REPETIDA,
                banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));
    }

    private static void conTransaccion() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        RepositorioQueFalla cuentas = new RepositorioQueFalla(almacen);
        ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, almacen, almacen, almacen, RELOJ, almacen);
        String ana = banco.abrir("Ana", 100_000_000).id();
        String luis = banco.abrir("Luis", 20_000_000).id();

        cuentas.fallarAlGuardar(luis);
        Comprobar.lanza("la transferencia falla a mitad", IllegalStateException.class,
                () -> banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));

        Comprobar.igual("a Ana no le salió nada", 100_000_000L, saldo(banco, ana));
        Comprobar.igual("el dinero total no cambió", 120_000_000L, saldo(banco, ana) + saldo(banco, luis));
        Comprobar.igual("el extracto de Ana solo tiene la apertura", 1, banco.movimientosDe(ana).size());

        cuentas.reparar();
        Comprobar.igual("la misma orden se puede reintentar", Resultado.REALIZADA,
                banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("y enviarla otra vez no transfiere de nuevo", Resultado.REPETIDA,
                banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("Ana pagó una sola vez", 70_000_000L, saldo(banco, ana));
        Comprobar.igual("Luis recibió una sola vez", 50_000_000L, saldo(banco, luis));
    }

    private static void bandejaDeSalida() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        RepositorioQueFalla cuentas = new RepositorioQueFalla(almacen);
        ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, almacen, almacen, almacen, RELOJ, almacen);
        String ana = banco.abrir("Ana", 100_000_000).id();
        String luis = banco.abrir("Luis", 20_000_000).id();
        List<EventoDeCuenta> recibidos = new ArrayList<>();

        cuentas.fallarAlGuardar(luis);
        Comprobar.lanza("una transferencia que falla", IllegalStateException.class,
                () -> banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("no deja ningún evento por entregar", 0, almacen.entregarPendientes(recibidos::add));

        cuentas.reparar();
        banco.transferir(new Orden("orden-1", ana, luis, 30_000_000));
        Comprobar.lanza("si el destino de los eventos está caído, la entrega falla", IllegalStateException.class,
                () -> almacen.entregarPendientes(evento -> {
                    throw new IllegalStateException("broker no disponible");
                }));
        Comprobar.igual("pero el evento no se pierde: sale en el siguiente intento", 1, almacen.entregarPendientes(recibidos::add));
        Comprobar.igual("y lleva un id estable para que el receptor descarte repeticiones", "transferencia:orden-1", recibidos.get(0).id());
        Comprobar.igual("no se entrega dos veces si no hace falta", 0, almacen.entregarPendientes(recibidos::add));
    }

    private static long saldo(ServicioDeCuentas banco, String cuentaId) {
        return banco.porId(cuentaId).orElseThrow().saldoCentavos();
    }
}
```

### Análisis

El código del servicio es **el mismo** en los dos casos. Lo único que cambia es el adaptador de `UnidadDeTrabajo` que se le conecta.

| | Sin unidad de trabajo | Con unidad de trabajo |
|---|---|---|
| Fallo entre el débito y el abono | Ana pierde el dinero y Luis no lo recibe | No cambia nada |
| Dinero total del banco | Faltan 300.000 pesos | Intacto |
| Reintentar la orden | Responde "repetida": la clave quedó anotada aunque la transferencia falló | Se ejecuta bien: la clave se revirtió junto con todo lo demás |

El último renglón es el que más enseña. La clave de idempotencia **sin** transacción empeora las cosas: convierte un fallo recuperable en uno permanente. Por eso se anota en la misma transacción.

## Errores comunes

- Abrir una transacción por cada llamada al repositorio y creer que la operación es atómica.
- Poner la transacción en el adaptador de entrada: el controlador decide qué va junto y el caso de uso deja de ser reutilizable.
- Capturar la excepción dentro de la transacción y seguir: se confirma un trabajo a medias.
- Anotar la clave de idempotencia fuera de la transacción.
- Publicar en un broker o llamar a otro servicio con la transacción abierta y dar por hecho que se deshará.
- Transacciones largas que retienen conexiones y bloqueos mientras esperan una respuesta externa.
- Creer que la transacción arregla la concurrencia. Atomicidad no es aislamiento: es la sesión siguiente.

## Taller

**Objetivo:** hacer que crear un pedido sea todo o nada, que una petición repetida no cree dos pedidos y que los eventos salgan con la transacción.

Sobre tu servicio de la sesión 03.

1. Retoma la pregunta que dejaste pendiente: crear un pedido descuenta existencias y guarda el pedido. Escribe una prueba que provoque un fallo entre las dos cosas, con un adaptador que falle a propósito. Ejecútala y anota qué queda mal.
2. Define un puerto de salida para agrupar operaciones. Decide su forma y su nombre.
3. Escribe el adaptador de memoria que lo cumpla: lo pendiente se confirma junto o se descarta. Puedes seguir la idea de este proyecto, pero el código debe ser tuyo y ajustado a tus puertos.
4. Envuelve cada caso de uso que escribe en su unidad de trabajo. Repite la prueba del paso 1: ahora no debe quedar nada a medias.
5. Decide qué casos de uso **no** necesitan transacción y explica por qué.
6. Añade una clave de idempotencia a "crear pedido". Demuestra con pruebas tres cosas: la misma petición dos veces crea un solo pedido; una petición que falló se puede reintentar con la misma clave; la segunda respuesta es de éxito, no de error.
7. Mueve los eventos a una bandeja de salida dentro de la transacción y escribe el mensajero que los entrega. Prueba que un pedido rechazado no deja eventos y que un destino caído no los pierde.
8. Tu caso de uso "confirmar pedido" debe avisar al cliente por correo. ¿Dónde va esa llamada respecto a la transacción? Escribe la respuesta y lo que ocurre en cada alternativa.
9. Busca en tus casos de uso algún `catch` dentro de la unidad de trabajo. Si hay alguno, explica qué se confirma cuando salta.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Un fallo a mitad de "crear pedido" no deja existencias descontadas | La prueba con el adaptador que falla |
| La misma petición dos veces crea un solo pedido | La prueba de idempotencia |
| Una petición fallida se puede reintentar con la misma clave | La prueba |
| Un pedido rechazado no deja eventos pendientes | La prueba de la bandeja |
| El dominio no menciona transacciones | Búsqueda en el paquete del dominio |

**Entrega:** rama `taller/hexagonal-04`, con el proyecto en `taller/solucion/` y `ANALISIS.md` con la salida del paso 1 y las respuestas de los pasos 5 y 8.

## En el trabajo real

En Spring, la unidad de trabajo es `@Transactional` sobre el método del servicio de aplicación, o un `TransactionTemplate` detrás de un puerto como el de aquí. Conviene saber qué hace por debajo: abre la transacción al entrar, la confirma al salir y la revierte si sale una `RuntimeException`. Con una excepción comprobada, **confirma** salvo que indiques `rollbackFor`. Y una llamada a un método `@Transactional` desde la misma clase no pasa por el proxy: no abre transacción.

Los proveedores de pago serios exponen la idempotencia en su API: una cabecera con la clave que elige el cliente. Es el mismo mecanismo de esta sesión.

## Preguntas senior

1. ¿Qué garantiza la atomicidad y qué no?
2. ¿Por qué la frontera de la transacción es el caso de uso?
3. ¿Por qué la clave de idempotencia debe anotarse en la misma transacción?
4. ¿Qué ocurre si publicas un evento en Kafka dentro de una transacción que luego se revierte?
5. ¿Qué problemas trae una transacción que dura segundos?
6. ¿Por qué la unidad de trabajo es un puerto y no una anotación en el dominio?
