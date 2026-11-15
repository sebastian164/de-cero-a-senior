# 05 — Concurrencia y aislamiento

## Idea

Dos personas editan la misma hoja de cálculo sin conexión. Las dos abren la versión de la mañana, cada una cambia una celda distinta y cada una guarda su archivo encima del original. Quien guarda de segundo borra, sin saberlo, el cambio del primero. Nadie hizo nada mal por separado; el problema apareció porque lo hicieron a la vez.

Hay dos formas clásicas de evitarlo. Una: quien abre el archivo lo bloquea y el otro espera. Otra: cualquiera puede abrirlo, pero al guardar se comprueba que nadie lo haya cambiado desde que lo abriste; si cambió, vuelves a empezar con la versión nueva.

## Lo esencial

### La actualización perdida

El caso de uso lee la cuenta, calcula el saldo nuevo en memoria y lo guarda. Con dos operaciones a la vez sobre la misma cuenta:

| Paso | Cajero 1 (consigna 100.000) | Cajero 2 (consigna 50.000) | Saldo guardado |
|---|---|---|---|
| 1 | Lee: 1.000.000 | | 1.000.000 |
| 2 | | Lee: 1.000.000 | 1.000.000 |
| 3 | Guarda 1.100.000 | | 1.100.000 |
| 4 | | Guarda 1.050.000 | **1.050.000** |

Se perdieron 100.000 pesos. Las dos transacciones fueron atómicas y las dos confirmaron: **la atomicidad no protege de esto**. Lo que falta es aislamiento.

### Las anomalías

| Anomalía | Qué pasa | En el banco |
|---|---|---|
| Lectura sucia | Lees un cambio que otro todavía no confirmó, y que luego revierte | Ves un abono que nunca existió |
| Lectura no repetible | Lees dos veces la misma fila en una transacción y cambió | El saldo que validaste ya no es el que hay al descontar |
| Lectura fantasma | Repites una consulta y aparecen o desaparecen filas | Sumas los movimientos del día dos veces y da distinto |
| Actualización perdida | Dos leen, calculan y escriben; el segundo pisa al primero | La tabla de arriba |
| Sesgo de escritura | Dos leen lo mismo, cada uno escribe en una fila **distinta**, y juntos rompen una regla | Un cliente con dos cuentas y la regla "la suma no puede ser negativa": dos retiros simultáneos, uno de cada cuenta, la violan |

### Los niveles de aislamiento

El estándar SQL define cuatro. A más aislamiento, menos anomalías y más espera o más reintentos.

| Nivel | Lectura sucia | No repetible | Fantasma | Notas |
|---|---|---|---|---|
| `READ UNCOMMITTED` | Posible | Posible | Posible | Casi nunca se usa |
| `READ COMMITTED` | No | Posible | Posible | Por defecto en PostgreSQL, Oracle y SQL Server |
| `REPEATABLE READ` | No | No | Posible según el estándar | Por defecto en MySQL (InnoDB) |
| `SERIALIZABLE` | No | No | No | El resultado equivale a ejecutar una tras otra |

Tres precisiones que importan en la práctica:

- **La actualización perdida no está en la tabla del estándar**, y con `READ COMMITTED` es posible si lees y luego escribes un valor calculado en la aplicación. Es el caso más común en código con ORM.
- **Cada motor implementa los niveles a su manera.** Lo que un nivel evita exactamente depende de la base de datos: hay que leer su documentación, no fiarse del nombre.
- **`SERIALIZABLE` no es gratis.** El motor cancela transacciones que no puede ordenar, y la aplicación debe reintentarlas.

### Cuatro formas de evitar la actualización perdida

| Forma | Cómo | Cuándo |
|---|---|---|
| Actualización atómica | `UPDATE cuenta SET saldo = saldo - :v WHERE id = :id AND saldo >= :v` | La regla cabe en una sentencia |
| Bloqueo optimista | Columna `version`; `UPDATE … WHERE id = :id AND version = :leida`. Si afecta cero filas, hubo conflicto | Pocos conflictos; reglas en el dominio |
| Bloqueo pesimista | `SELECT … FOR UPDATE`: la fila queda bloqueada hasta el final de la transacción | Muchos conflictos sobre las mismas filas |
| `SERIALIZABLE` con reintento | El motor detecta el conflicto | Reglas que cruzan varias filas |

En una arquitectura hexagonal con reglas en el dominio, la opción natural es el **bloqueo optimista**: el dominio lee, decide y el adaptador comprueba al guardar que nadie se adelantó. Es lo que hace este proyecto.

### Bloqueo optimista

1. Cada cuenta lleva un número de `version`.
2. Al leerla, te llevas la versión que tenía.
3. Al guardarla, el adaptador exige que la versión guardada siga siendo la que leíste. Si lo es, guarda y la incrementa. Si no, lanza `ConflictoDeConcurrencia`.
4. El caso de uso **vuelve a empezar desde el principio**: lee otra vez, decide otra vez. Las reglas se reevalúan con datos frescos.

| Regla del reintento | Por qué |
|---|---|
| Se repite el caso de uso entero, no solo el guardado | La decisión se tomó con datos viejos |
| Tiene un máximo de intentos | Con mucha contención, reintentar sin fin empeora todo |
| Conviene esperar un poco, con algo de azar, entre intentos | Si no, los mismos chocan otra vez en el mismo instante |
| Lo reintentado debe poder repetirse sin efectos fuera de la transacción | Un correo enviado en el primer intento no se deshace |

En este proyecto, `conReintento` de `ServicioDeCuentas` hace tres intentos sin espera. Es lo mínimo para ver el mecanismo; en producción añade la espera.

### Bloqueo pesimista e interbloqueos

Con `SELECT … FOR UPDATE` nadie más puede cambiar esas filas hasta que termines. No hay reintentos, pero aparece otro riesgo:

| Transferencia 1: Ana → Luis | Transferencia 2: Luis → Ana |
|---|---|
| Bloquea la cuenta de Ana | Bloquea la cuenta de Luis |
| Espera la de Luis | Espera la de Ana |

Ninguna avanza: es un **interbloqueo**. La base de datos lo detecta y cancela una de las dos. Se evita bloqueando siempre **en el mismo orden**, por ejemplo por identificador:

```sql
BEGIN;
SELECT * FROM cuenta WHERE id IN ('cuenta-1', 'cuenta-2') ORDER BY id FOR UPDATE;
-- reglas y actualizaciones
COMMIT;
```

| | Optimista | Pesimista |
|---|---|---|
| Quien llega segundo | Falla y reintenta | Espera |
| Coste sin conflictos | Casi ninguno | Bloqueos en cada operación |
| Coste con muchos conflictos | Muchos reintentos | Colas de espera |
| Interbloqueos | No | Sí, si no se ordena |
| Encaja con | Lecturas frecuentes, conflictos raros | Filas muy disputadas |

### Lo que ofrece el adaptador de memoria

`AlmacenEnMemoria` se comporta como una base de datos en `READ COMMITTED` con bloqueo optimista: solo se ve lo confirmado, y al guardar y al confirmar se comprueba la versión. Es, a propósito, la misma combinación que tendrás con JPA en la sesión 07.

## El código

### Dos dobles de prueba

Un repositorio sin control de versión, para ver el problema:

<!-- file: ../proyecto/pruebas/banco/RepositorioIngenuo.java -->
```java
package banco;

import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Un repositorio que guarda lo que le den, sin mirar la versión: la última escritura gana. */
public final class RepositorioIngenuo implements RepositorioDeCuentas {

    private final Map<String, Cuenta> cuentas = new HashMap<>();
    private int secuencia = 0;

    @Override
    public String siguienteId() {
        return "cuenta-" + (++secuencia);
    }

    @Override
    public void guardar(Cuenta cuenta) {
        cuentas.put(cuenta.id(), copia(cuenta));
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        return Optional.ofNullable(cuentas.get(id)).map(RepositorioIngenuo::copia);
    }

    private static Cuenta copia(Cuenta cuenta) {
        return Cuenta.existente(cuenta.id(), cuenta.titular(), cuenta.saldo(), cuenta.enviadoHoy(), cuenta.diaDelTope(), cuenta.version());
    }
}
```

Y uno que deja colarse a "otro" justo después de una lectura, para provocar el conflicto en el momento exacto:

<!-- file: ../proyecto/pruebas/banco/RepositorioConInterferencia.java -->
```java
package banco;

import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import java.util.Optional;

/** Envuelve un repositorio real y, justo después de una lectura, deja actuar a otro hilo. */
public final class RepositorioConInterferencia implements RepositorioDeCuentas {

    private final RepositorioDeCuentas real;
    private Runnable interferencia;
    private int vecesPendientes;
    private int lecturas;

    public RepositorioConInterferencia(RepositorioDeCuentas real) {
        this.real = real;
    }

    /** Lo que hará "el otro" después de cada una de las próximas lecturas, tantas veces como se indique. */
    public void interferir(int veces, Runnable interferencia) {
        this.vecesPendientes = veces;
        this.interferencia = interferencia;
        this.lecturas = 0;
    }

    public int lecturas() {
        return lecturas;
    }

    @Override
    public String siguienteId() {
        return real.siguienteId();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        real.guardar(cuenta);
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        Optional<Cuenta> leida = real.buscar(id);
        if (Thread.currentThread().getName().equals("el-otro")) {
            return leida;                                        // el otro no se interfiere a sí mismo
        }
        lecturas++;
        if (vecesPendientes > 0) {
            vecesPendientes--;
            enOtroHilo(interferencia);                           // otro hilo es otra transacción
        }
        return leida;                                            // lo leído ya es viejo
    }

    private static void enOtroHilo(Runnable accion) {
        Thread otro = new Thread(accion, "el-otro");
        otro.start();
        try {
            otro.join();
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
    }
}
```

### Las pruebas

<!-- file: ../proyecto/pruebas/banco/PruebasDeConcurrencia.java -->
```java
package banco;

import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** La actualización perdida, el bloqueo optimista, el reintento y una prueba con muchos hilos. */
public final class PruebasDeConcurrencia {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");
    private static final LocalDate HOY = LocalDate.parse("2026-11-15");

    private PruebasDeConcurrencia() {
    }

    public static void ejecutar() {
        System.out.println("Concurrencia, sin control de versión:");
        Comprobar.igual("la segunda consignación pisa a la primera: se pierden 100.000 pesos",
                105_000_000L, dosCajerosALaVez(new RepositorioIngenuo()));

        System.out.println("Concurrencia, con control de versión:");
        Comprobar.lanza("el segundo cajero recibe un conflicto en lugar de pisar al primero",
                ConflictoDeConcurrencia.class, () -> dosCajerosALaVez(new AlmacenEnMemoria()));
        elCasoDeUsoReintenta();
        elCasoDeUsoSeRinde();
        muchosHilos();
    }

    /** Dos cajeros leen la misma cuenta, cada uno consigna y cada uno guarda. Devuelve el saldo final. */
    private static long dosCajerosALaVez(RepositorioDeCuentas cuentas) {
        String id = cuentas.siguienteId();
        cuentas.guardar(Cuenta.abrir(id, "Ana", Dinero.pesos(1_000_000), HOY));

        Cuenta laDelCajero1 = cuentas.buscar(id).orElseThrow();  // los dos leen 1.000.000
        Cuenta laDelCajero2 = cuentas.buscar(id).orElseThrow();

        laDelCajero1.depositar(Dinero.pesos(100_000));
        cuentas.guardar(laDelCajero1);                           // queda 1.100.000

        laDelCajero2.depositar(Dinero.pesos(50_000));
        cuentas.guardar(laDelCajero2);                           // 1.050.000 encima, o conflicto

        return cuentas.buscar(id).orElseThrow().saldo().centavos();
    }

    private static void elCasoDeUsoReintenta() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        RepositorioConInterferencia cuentas = new RepositorioConInterferencia(almacen);
        ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, almacen, almacen, almacen, RELOJ, almacen);
        String ana = banco.abrir("Ana", 100_000_000).id();
        String luis = banco.abrir("Luis", 20_000_000).id();

        // Mientras la transferencia tiene la cuenta de Ana en la mano, otro le consigna 50.000 pesos.
        cuentas.interferir(1, () -> banco.depositar(ana, 5_000_000));
        Resultado resultado = banco.transferir(new Orden("orden-1", ana, luis, 30_000_000));

        Comprobar.igual("la transferencia termina bien", Resultado.REALIZADA, resultado);
        Comprobar.igual("necesitó dos intentos: cuatro lecturas en lugar de dos", 4, cuentas.lecturas());
        Comprobar.igual("no se perdió la consignación ni la transferencia", 75_000_000L, saldo(banco, ana));
        Comprobar.igual("Luis recibió una sola vez", 50_000_000L, saldo(banco, luis));
        Comprobar.igual("y hay un solo envío en el extracto de Ana", 3, banco.movimientosDe(ana).size());
    }

    private static void elCasoDeUsoSeRinde() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        RepositorioConInterferencia cuentas = new RepositorioConInterferencia(almacen);
        ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, almacen, almacen, almacen, RELOJ, almacen);
        String ana = banco.abrir("Ana", 100_000_000).id();
        String luis = banco.abrir("Luis", 20_000_000).id();

        // Alguien cambia la cuenta de Ana en cada lectura: el conflicto no cede.
        cuentas.interferir(100, () -> banco.depositar(ana, 1_000_000));
        Comprobar.lanza("tras tres intentos, el conflicto llega a quien llamó", ConflictoDeConcurrencia.class,
                () -> banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        cuentas.interferir(0, null);

        Comprobar.igual("a Luis no le llegó nada", 20_000_000L, saldo(banco, luis));
        Comprobar.igual("Ana solo tiene la apertura y las consignaciones del otro", 0L,
                banco.movimientosDe(ana).stream().filter(movimiento -> movimiento.referencia().equals("orden-1")).count());
        Comprobar.igual("y la orden se puede volver a enviar", Resultado.REALIZADA,
                banco.transferir(new Orden("orden-1", ana, luis, 30_000_000)));
    }

    private static void muchosHilos() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, almacen, RELOJ, almacen);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(banco.abrir("Titular " + i, 50_000_000).id());
        }
        int hilos = 8;
        int porHilo = 200;
        AtomicInteger realizadas = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();
        AtomicInteger otrosErrores = new AtomicInteger();

        try (ExecutorService grupo = Executors.newFixedThreadPool(hilos)) {
            for (int h = 0; h < hilos; h++) {
                int hilo = h;
                grupo.submit(() -> {
                    for (int i = 0; i < porHilo; i++) {
                        String origen = ids.get((hilo + i) % 4);
                        String destino = ids.get((hilo + i + 1 + i % 3) % 4);
                        try {
                            banco.transferir(new Orden("hilo-" + hilo + "-" + i, origen, destino, 10_000));
                            realizadas.incrementAndGet();
                        } catch (ConflictoDeConcurrencia conflicto) {
                            conflictos.incrementAndGet();
                        } catch (RuntimeException error) {
                            otrosErrores.incrementAndGet();
                        }
                    }
                });
            }
        }                                                        // al cerrar, espera a que terminen todos

        long total = ids.stream().mapToLong(id -> saldo(banco, id)).sum();
        long renglones = ids.stream().mapToLong(id -> banco.movimientosDe(id).size()).sum();
        System.out.println("  (" + realizadas + " realizadas, " + conflictos + " rendidas por conflicto)");
        Comprobar.igual("con 8 hilos y 1.600 transferencias, el dinero total es el mismo", 200_000_000L, total);
        Comprobar.igual("cada transferencia se hizo entera o no se hizo", hilos * porHilo, realizadas.get() + conflictos.get());
        Comprobar.igual("ningún otro error", 0, otrosErrores.get());
        Comprobar.igual("el extracto cuadra: dos renglones por transferencia realizada", 4L + 2L * realizadas.get(), renglones);
    }

    private static long saldo(ServicioDeCuentas banco, String cuentaId) {
        return banco.porId(cuentaId).orElseThrow().saldoCentavos();
    }
}
```

### Análisis

| Prueba | Qué demuestra |
|---|---|
| Dos cajeros con el repositorio ingenuo | La actualización perdida ocurre sin que nadie reciba un error |
| Dos cajeros con control de versión | El segundo se entera de que trabajaba con datos viejos |
| El caso de uso reintenta | Lee de nuevo, decide de nuevo y termina bien; no se pierde ningún cambio |
| El caso de uso se rinde | Hay un límite; al agotarlo no queda nada a medias y la orden sigue disponible |
| Muchos hilos | El dinero se conserva aunque el orden de ejecución sea impredecible |

El número de transferencias realizadas y rendidas cambia en cada ejecución: depende de cómo el sistema operativo reparta los hilos. Lo que **no** cambia son las cuatro comprobaciones. Esa es la forma de probar código concurrente: no se afirma qué pasó, se afirma lo que debe ser cierto pase lo que pase.

Y fíjate en dónde vive cada cosa: la versión viaja en `Cuenta`, la comprobación está en el adaptador, el reintento en el caso de uso. Las reglas del dominio no cambiaron ni una línea para soportar concurrencia.

## Errores comunes

- Creer que `@Transactional` o una transacción "arreglan" la concurrencia.
- Leer, calcular en la aplicación y escribir sin versión ni bloqueo.
- Reintentar solo el guardado, con la decisión tomada sobre datos viejos.
- Reintentar sin límite y sin espera.
- Capturar el conflicto y devolver éxito.
- Bloqueos pesimistas tomados en orden distinto según la operación.
- Subir el nivel de aislamiento a `SERIALIZABLE` sin código que reintente.
- Probar concurrencia con una sola ejecución que "funcionó en mi máquina".
- Proteger con `synchronized` en la aplicación cuando hay más de una instancia desplegada: el bloqueo no sale del proceso.

## Taller

**Objetivo:** impedir que dos pedidos simultáneos vendan la misma unidad, y demostrarlo con pruebas.

Sobre tu servicio de la sesión 04.

1. Escribe una prueba determinista del problema: queda una unidad de un producto y dos pedidos la leen antes de que ninguno guarde. Con tu adaptador actual, ¿qué ocurre? Anota el resultado.
2. Decide qué es lo que se disputa: ¿el pedido, las existencias de cada producto o las dos cosas? Justifica dónde va la versión.
3. Implementa el control de versión en tu adaptador de memoria y una excepción de conflicto en la aplicación.
4. Añade el reintento al caso de uso. Decide el máximo de intentos y qué recibe quien llamó cuando se agotan.
5. Escribe un doble de prueba que provoque el conflicto en el momento exacto, y con él prueba dos cosas: que el caso de uso termina bien al segundo intento, y que al rendirse no deja existencias descontadas.
6. Escribe una prueba con varios hilos que creen pedidos del mismo producto. Define **antes** de ejecutarla qué debe ser cierto siempre. Como mínimo: las existencias nunca quedan negativas, y las unidades vendidas más las que quedan suman las iniciales.
7. Ejecuta la prueba del paso 6 diez veces seguidas. Anota cuántos pedidos se rindieron en cada una.
8. Un pedido con dos productos necesita existencias de ambos. Si usaras bloqueo pesimista, ¿en qué orden bloquearías? Describe el interbloqueo que aparece si no lo ordenas.
9. Regla nueva: "un cliente no puede tener más de tres pedidos pendientes". Dos peticiones simultáneas del mismo cliente crean el cuarto y el quinto. ¿Tu control de versión lo evita? Explica por qué y propón una solución.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Dos pedidos simultáneos no venden la misma unidad | La prueba determinista |
| El caso de uso reintenta y tiene un límite | Las pruebas con el doble |
| La prueba con hilos pasa diez veces seguidas | Ejecución repetida |
| Las afirmaciones no dependen del orden de ejecución | Lectura de la prueba |
| El dominio no cambió para soportar concurrencia | `git diff` del paquete del dominio |

**Entrega:** rama `taller/hexagonal-05`, con el proyecto en `taller/solucion/` y `ANALISIS.md` con los resultados de los pasos 1 y 7 y las respuestas de los pasos 8 y 9.

## En el trabajo real

Con JPA, el bloqueo optimista es un campo anotado con `@Version`: Hibernate añade `AND version = ?` a cada `UPDATE` y lanza `OptimisticLockException` si no afecta ninguna fila. El bloqueo pesimista es `@Lock(LockModeType.PESSIMISTIC_WRITE)` sobre una consulta. Ninguno de los dos reintenta por ti: el reintento sigue siendo tuyo.

El aislamiento por defecto lo decide la base de datos, no el framework. Antes de afirmar qué protege tu sistema, mira qué motor usas y qué nivel tiene configurado.

Cuando hay varias instancias del servicio, la única coordinación fiable es la que pasa por la base de datos. Esto se cruza con el tamaño del pool de conexiones: los bloqueos largos retienen conexiones. Está en la ruta de [HikariCP](../../hikaricp/).

## Preguntas senior

1. ¿Por qué una transacción atómica no evita la actualización perdida?
2. ¿Qué diferencia hay entre bloqueo optimista y pesimista, y cuándo eliges cada uno?
3. ¿Por qué el reintento repite el caso de uso entero?
4. ¿Qué es el sesgo de escritura y por qué una columna de versión no lo detecta?
5. ¿Cómo se evita un interbloqueo entre dos transferencias cruzadas?
6. ¿Cómo pruebas código concurrente si el resultado cambia en cada ejecución?
