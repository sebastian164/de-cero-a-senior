# 06 — Una saga en un servicio hexagonal

> Requiere el módulo de [arquitectura hexagonal](../../../hexagonal/), sobre todo las sesiones de [transacciones](../../../hexagonal/04-transacciones/) y [puertos](../../../hexagonal/03-puertos-y-casos-de-uso/). El ejemplo se compila **junto con el núcleo del banco**, sin modificarlo.

## Idea

Enviar dinero a alguien de otro banco. Tu banco puede sacar el dinero de tu cuenta de forma segura: es su base de datos. Lo que no puede es meterlo en la cuenta del otro banco en la misma operación: es la base de otro. Entre "salió de aquí" y "entró allá" hay una red, y por la red las respuestas a veces no vuelven.

Dentro de un banco, eso se resolvía con una transacción. Entre dos bancos no hay transacción posible. Hay una saga.

## Lo esencial

### De la transacción a la saga

| | Transferencia dentro del banco | Envío a otro banco |
|---|---|---|
| Recursos | Una base de datos | Dos bases, de dos dueños |
| Mecanismo | Transacción ACID ([hexagonal 04](../../../hexagonal/04-transacciones/)) | Saga: transacciones locales encadenadas |
| Estados intermedios | No existen para nadie | Existen y son visibles: el dinero salió y aún no llegó |
| Si falla a mitad | Se revierte sola | Hay que **compensar** con otra operación |
| Consistencia | Inmediata | Eventual |

### Dónde va cada pieza de la saga

| Pieza | Qué es en la arquitectura |
|---|---|
| El orquestador | Un **servicio de aplicación**: coordina, no contiene reglas de dinero |
| Cada paso local (debitar, devolver) | Dominio, dentro de una unidad de trabajo |
| Cada paso remoto (avisar al otro banco) | Un **puerto de salida**; el adaptador decide si es HTTP, Kafka o lo que sea |
| El estado de la saga | Una entidad con su repositorio, **en la misma base** que los datos locales |
| Reanudar las sagas abiertas | Otro puerto de entrada, que llama una tarea programada |
| La idempotencia de cada paso | El identificador de la saga viaja en cada llamada |

### La regla que lo sostiene todo

**El cambio local y el avance del estado de la saga van en la misma transacción.** Si el débito se confirma, queda constancia de que hay una saga abierta. Si no queda constancia, no hubo débito.

Y su contraparte: **la llamada remota va fuera de toda transacción**. Es la regla de "transacciones cortas" llevada a su consecuencia.

```
  transacción local 1          sin transacción             transacción local 2
  ┌─────────────────────┐      ┌───────────────┐           ┌───────────────────────────┐
  │ debitar la cuenta   │ ───► │ avisar al otro│ ───────►  │ aceptado: cerrar          │
  │ guardar la saga:    │      │ banco         │           │ rechazado: devolver y     │
  │   DEBITADO          │      └───────────────┘           │            cerrar         │
  └─────────────────────┘        sin respuesta:            └───────────────────────────┘
                                 la saga se queda en DEBITADO y se reintenta después
```

### Sin respuesta no significa rechazado

Es el error más caro de las sagas. Si la red no responde, hay tres posibilidades y desde aquí no se distinguen:

| Qué pasó | Si compensas | Si reintentas |
|---|---|---|
| El otro banco no recibió nada | Correcto | Correcto |
| Lo recibió y lo aceptó, pero la respuesta se perdió | **El dinero queda en los dos lados** | Correcto, si el otro deduplica |
| Lo recibió y lo rechazó, y la respuesta se perdió | Correcto | Correcto: volverá a rechazarlo |

Reintentar es seguro en los tres casos con una sola condición: que el otro lado sea idempotente. Por eso el identificador de la saga viaja en cada llamada.

### Compensar no es deshacer

La devolución no borra el débito: añade un abono. En el extracto quedan los dos renglones. Y puede haber efectos que no vuelven atrás: en este banco, lo enviado sigue contando para el tope diario aunque se devuelva. Si eso está bien o mal lo decide el negocio, no la técnica.

## Ejemplo

Java puro, sobre el banco del módulo hexagonal. Reutiliza `Cuenta`, sus puertos y su unidad de trabajo; añade un caso de uso nuevo con sus propios puertos.

```
ejemplo/src/
  interbancaria/aplicacion/Envio.java                  el estado de la saga
  interbancaria/aplicacion/EnviarAOtroBanco.java       puertos de entrada
  interbancaria/aplicacion/PuertosDeSalida.java        la red y el repositorio de envíos
  interbancaria/aplicacion/ServicioDeEnvios.java       el orquestador
  interbancaria/adaptadores/EnviosEnMemoria.java       repositorio de sagas
  interbancaria/adaptadores/RedSimulada.java           el otro banco, con fallos a voluntad
  Main.java
```

### El estado de la saga

<!-- file: ejemplo/src/interbancaria/aplicacion/Envio.java -->
```java
package interbancaria.aplicacion;

/** Un envío a otro banco y en qué punto va. Es lo que permite retomarlo después de una caída. */
public final class Envio {

    public enum Estado {
        DEBITADO,        // el dinero salió de la cuenta; aún no se sabe si llegó
        COMPLETADO,      // el otro banco lo aceptó
        DEVUELTO         // el otro banco lo rechazó y el dinero volvió a la cuenta
    }

    private final String id;
    private final String cuentaOrigen;
    private final String bancoDestino;
    private final String cuentaDestino;
    private final long centavos;
    private Estado estado;

    public Envio(String id, String cuentaOrigen, String bancoDestino, String cuentaDestino, long centavos, Estado estado) {
        this.id = id;
        this.cuentaOrigen = cuentaOrigen;
        this.bancoDestino = bancoDestino;
        this.cuentaDestino = cuentaDestino;
        this.centavos = centavos;
        this.estado = estado;
    }

    public void completar() {
        exigirAbierto();
        estado = Estado.COMPLETADO;
    }

    public void devolver() {
        exigirAbierto();
        estado = Estado.DEVUELTO;
    }

    /** Un envío cerrado no cambia: ni se completa lo devuelto ni se devuelve lo completado. */
    private void exigirAbierto() {
        if (estado != Estado.DEBITADO) {
            throw new IllegalStateException("el envío " + id + " ya está " + estado);
        }
    }

    public Envio copia() {
        return new Envio(id, cuentaOrigen, bancoDestino, cuentaDestino, centavos, estado);
    }

    public String id() {
        return id;
    }

    public String cuentaOrigen() {
        return cuentaOrigen;
    }

    public String bancoDestino() {
        return bancoDestino;
    }

    public String cuentaDestino() {
        return cuentaDestino;
    }

    public long centavos() {
        return centavos;
    }

    public Estado estado() {
        return estado;
    }
}
```

### Los puertos

<!-- file: ejemplo/src/interbancaria/aplicacion/EnviarAOtroBanco.java -->
```java
package interbancaria.aplicacion;

/** Puertos de entrada de la saga. */
public interface EnviarAOtroBanco {

    /** El id lo elige quien envía la orden: repetirla no envía dos veces. */
    record Orden(String id, String cuentaOrigen, String bancoDestino, String cuentaDestino, long centavos) {
    }

    /** Devuelve el estado en que quedó. DEBITADO significa "en curso": se cerrará más tarde. */
    Envio.Estado enviar(Orden orden);

    /** Retoma los envíos que quedaron abiertos. Lo llama una tarea programada. Devuelve cuántos cerró. */
    int reanudarPendientes();
}
```

<!-- file: ejemplo/src/interbancaria/aplicacion/PuertosDeSalida.java -->
```java
package interbancaria.aplicacion;

import java.util.List;
import java.util.Optional;

/** Lo que la saga necesita del exterior, además de los puertos que ya tenía el banco. */
public final class PuertosDeSalida {

    private PuertosDeSalida() {
    }

    public interface RedInterbancaria {

        enum Respuesta {
            ACEPTADO,
            RECHAZADO
        }

        /** El id del envío viaja siempre: el otro banco lo usa para no abonar dos veces. */
        Respuesta enviar(String idDelEnvio, String bancoDestino, String cuentaDestino, long centavos);
    }

    /** No hubo respuesta. No dice si el otro banco recibió la orden o no: no se sabe. */
    public static final class RedSinRespuesta extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public RedSinRespuesta(String detalle) {
            super(detalle);
        }
    }

    public interface RepositorioDeEnvios {

        Optional<Envio> buscar(String id);

        void guardar(Envio envio);

        List<Envio> abiertos();
    }
}
```

### El orquestador

<!-- file: ejemplo/src/interbancaria/aplicacion/ServicioDeEnvios.java -->
```java
package interbancaria.aplicacion;

import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import interbancaria.aplicacion.Envio.Estado;
import interbancaria.aplicacion.PuertosDeSalida.RedInterbancaria;
import interbancaria.aplicacion.PuertosDeSalida.RedInterbancaria.Respuesta;
import interbancaria.aplicacion.PuertosDeSalida.RedSinRespuesta;
import interbancaria.aplicacion.PuertosDeSalida.RepositorioDeEnvios;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

/** La saga orquestada, como servicio de aplicación: transacciones locales cortas y, entre ellas, la red. */
public final class ServicioDeEnvios implements EnviarAOtroBanco {

    private final RepositorioDeCuentas cuentas;
    private final RegistroDeMovimientos movimientos;
    private final RepositorioDeEnvios envios;
    private final RedInterbancaria red;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Reloj reloj;

    public ServicioDeEnvios(RepositorioDeCuentas cuentas, RegistroDeMovimientos movimientos, RepositorioDeEnvios envios,
                            RedInterbancaria red, UnidadDeTrabajo unidadDeTrabajo, Reloj reloj) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.envios = envios;
        this.red = red;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.reloj = reloj;
    }

    @Override
    public Estado enviar(Orden orden) {
        // Paso 1. Transacción local: sale el dinero y queda constancia de la saga. Las dos cosas o ninguna.
        Envio envio = unidadDeTrabajo.ejecutar(() -> {
            Optional<Envio> yaExiste = envios.buscar(orden.id());
            if (yaExiste.isPresent()) {
                return yaExiste.get();                           // orden repetida: se retoma donde iba
            }
            Instant ahora = reloj.ahora();
            Cuenta origen = exigir(orden.cuentaOrigen());
            Dinero valor = new Dinero(orden.centavos());
            origen.enviar(valor, LocalDate.ofInstant(ahora, ZoneOffset.UTC));   // saldo y tope: reglas del dominio
            cuentas.guardar(origen);
            movimientos.registrar(new Movimiento(origen.id(), Movimiento.Tipo.TRANSFERENCIA_ENVIADA, valor, ahora, orden.id()));
            Envio nuevo = new Envio(orden.id(), orden.cuentaOrigen(), orden.bancoDestino(), orden.cuentaDestino(),
                    orden.centavos(), Estado.DEBITADO);
            envios.guardar(nuevo);
            return nuevo;
        });
        return avanzar(envio);
    }

    @Override
    public int reanudarPendientes() {
        int cerrados = 0;
        for (Envio abierto : envios.abiertos()) {
            if (avanzar(abierto) != Estado.DEBITADO) {
                cerrados++;
            }
        }
        return cerrados;
    }

    private Estado avanzar(Envio envio) {
        if (envio.estado() != Estado.DEBITADO) {
            return envio.estado();                               // ya estaba cerrado
        }

        // Paso 2. Sin transacción abierta: la red es lenta y puede no responder.
        Respuesta respuesta;
        try {
            respuesta = red.enviar(envio.id(), envio.bancoDestino(), envio.cuentaDestino(), envio.centavos());
        } catch (RedSinRespuesta sinRespuesta) {
            return Estado.DEBITADO;                              // no se sabe qué pasó: NO se devuelve. Se reintentará
        }

        // Paso 3. Transacción local: cerrar la saga, y devolver el dinero si el otro banco lo rechazó.
        return unidadDeTrabajo.ejecutar(() -> {
            Envio actual = envios.buscar(envio.id()).orElseThrow();
            if (actual.estado() != Estado.DEBITADO) {
                return actual.estado();                          // otro intento la cerró antes
            }
            if (respuesta == Respuesta.ACEPTADO) {
                actual.completar();
            } else {
                Cuenta origen = exigir(actual.cuentaOrigen());   // la compensación: una operación nueva, no un "deshacer"
                Dinero valor = new Dinero(actual.centavos());
                origen.depositar(valor);
                cuentas.guardar(origen);
                movimientos.registrar(new Movimiento(origen.id(), Movimiento.Tipo.DEPOSITO, valor, reloj.ahora(),
                        "devolución de " + actual.id()));
                actual.devolver();
            }
            envios.guardar(actual);
            return actual.estado();
        });
    }

    private Cuenta exigir(String cuentaId) {
        return cuentas.buscar(cuentaId).orElseThrow(() -> new CuentaNoEncontrada(cuentaId));
    }
}
```

Lee `enviar` y `avanzar` buscando dos cosas. Una: no hay ninguna regla de dinero; el saldo y el tope los decide `Cuenta`. Otra: la llamada a `red.enviar` no está dentro de ningún `unidadDeTrabajo.ejecutar`.

### Los adaptadores

El repositorio de envíos debe confirmar y revertir **junto con** las cuentas. En una base de datos real eso sale gratis: la tabla de envíos está en la misma base y entra en la misma transacción. En memoria hay que construirlo: este adaptador envuelve la unidad de trabajo del banco y restaura sus datos si la transacción falla.

<!-- file: ejemplo/src/interbancaria/adaptadores/EnviosEnMemoria.java -->
```java
package interbancaria.adaptadores;

import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import interbancaria.aplicacion.Envio;
import interbancaria.aplicacion.PuertosDeSalida.RepositorioDeEnvios;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Repositorio de sagas en memoria que participa en la transacción del banco. Para un solo hilo. */
public final class EnviosEnMemoria implements RepositorioDeEnvios {

    private Map<String, Envio> envios = new LinkedHashMap<>();

    @Override
    public Optional<Envio> buscar(String id) {
        return Optional.ofNullable(envios.get(id)).map(Envio::copia);
    }

    @Override
    public void guardar(Envio envio) {
        envios.put(envio.id(), envio.copia());
    }

    @Override
    public List<Envio> abiertos() {
        return envios.values().stream().filter(envio -> envio.estado() == Envio.Estado.DEBITADO).map(Envio::copia).toList();
    }

    /** Una unidad de trabajo que abarca las cuentas y los envíos: si la del banco falla, los envíos vuelven atrás. */
    public UnidadDeTrabajo juntoCon(UnidadDeTrabajo delBanco) {
        return new UnidadDeTrabajo() {
            @Override
            public <T> T ejecutar(Supplier<T> trabajo) {
                Map<String, Envio> antes = new LinkedHashMap<>(envios);
                try {
                    return delBanco.ejecutar(trabajo);
                } catch (RuntimeException error) {
                    envios = antes;
                    throw error;
                }
            }
        };
    }
}
```

<!-- file: ejemplo/src/interbancaria/adaptadores/RedSimulada.java -->
```java
package interbancaria.adaptadores;

import interbancaria.aplicacion.PuertosDeSalida.RedInterbancaria;
import interbancaria.aplicacion.PuertosDeSalida.RedSinRespuesta;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** El otro banco y la red que lleva hasta él, con fallos programables. */
public final class RedSimulada implements RedInterbancaria {

    private final Set<String> cuentasQueExisten = new HashSet<>();
    private final Map<String, Respuesta> yaAtendidos = new LinkedHashMap<>();   // el otro banco también deduplica
    private long recibidoCentavos = 0;
    private int llamadas = 0;
    private int respuestasPorPerder = 0;
    private int caidasPorOcurrir = 0;

    public void existe(String cuentaDestino) {
        cuentasQueExisten.add(cuentaDestino);
    }

    /** Las próximas llamadas llegan al otro banco, pero la respuesta se pierde por el camino. */
    public void perderRespuestas(int cuantas) {
        respuestasPorPerder = cuantas;
    }

    /** En las próximas llamadas, nuestro proceso muere antes de poder llamar. */
    public void matarElProceso(int cuantas) {
        caidasPorOcurrir = cuantas;
    }

    @Override
    public Respuesta enviar(String idDelEnvio, String bancoDestino, String cuentaDestino, long centavos) {
        if (caidasPorOcurrir > 0) {
            caidasPorOcurrir--;
            throw new IllegalStateException("el proceso murió antes de llamar al otro banco");
        }
        llamadas++;
        Respuesta respuesta = yaAtendidos.get(idDelEnvio);
        if (respuesta == null) {                                 // primera vez que el otro banco ve este envío
            respuesta = cuentasQueExisten.contains(cuentaDestino) ? Respuesta.ACEPTADO : Respuesta.RECHAZADO;
            yaAtendidos.put(idDelEnvio, respuesta);
            if (respuesta == Respuesta.ACEPTADO) {
                recibidoCentavos += centavos;
            }
        }
        if (respuestasPorPerder > 0) {
            respuestasPorPerder--;
            throw new RedSinRespuesta("tiempo de espera agotado");
        }
        return respuesta;
    }

    public long recibidoCentavos() {
        return recibidoCentavos;
    }

    public int llamadas() {
        return llamadas;
    }
}
```

### La demostración

<!-- file: ejemplo/src/Main.java -->
```java
import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.ServicioDeCuentas;
import banco.dominio.Movimiento;
import banco.dominio.ReglaDeNegocioViolada;
import interbancaria.adaptadores.EnviosEnMemoria;
import interbancaria.adaptadores.RedSimulada;
import interbancaria.aplicacion.EnviarAOtroBanco.Orden;
import interbancaria.aplicacion.Envio.Estado;
import interbancaria.aplicacion.ServicioDeEnvios;
import java.time.Instant;
import java.util.List;

/** El banco del módulo hexagonal, más una saga para enviar dinero a otro banco. */
public class Main {

    private static int fallos = 0;

    public static void main(String[] args) {
        // ---- Composición: los mismos adaptadores del banco, más los de la saga ----
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        EnviosEnMemoria sagas = new EnviosEnMemoria();
        RedSimulada red = new RedSimulada();
        red.existe("cuenta-de-marta");
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, almacen,
                () -> Instant.parse("2026-11-15T10:00:00Z"), almacen);
        ServicioDeEnvios envios = new ServicioDeEnvios(almacen, almacen, sagas, red, sagas.juntoCon(almacen),
                () -> Instant.parse("2026-11-15T10:00:00Z"));

        String ana = banco.abrir("Ana", 100_000_000).id();       // 1.000.000 de pesos

        System.out.println("1. El otro banco acepta");
        Estado uno = envios.enviar(new Orden("envio-1", ana, "otro-banco", "cuenta-de-marta", 20_000_000));
        comprobar("el envío queda completado", uno == Estado.COMPLETADO);
        comprobar("a Ana le salieron 200.000 pesos", saldo(banco, ana) == 80_000_000);

        System.out.println("2. El otro banco rechaza: la cuenta de destino no existe");
        Estado dos = envios.enviar(new Orden("envio-2", ana, "otro-banco", "cuenta-inventada", 15_000_000));
        comprobar("el envío queda devuelto", dos == Estado.DEVUELTO);
        comprobar("el saldo de Ana vuelve a ser el de antes", saldo(banco, ana) == 80_000_000);
        List<Movimiento> delEnvio2 = banco.movimientosDe(ana).stream()
                .filter(movimiento -> movimiento.referencia().contains("envio-2")).toList();
        delEnvio2.forEach(movimiento -> System.out.println("     " + movimiento.tipo() + " " + movimiento.valor().centavos() / 100
                + " (" + movimiento.referencia() + ")"));
        comprobar("en el extracto quedan los dos renglones: compensar no es borrar", delEnvio2.size() == 2);

        System.out.println("3. El otro banco acepta, pero la respuesta se pierde");
        red.perderRespuestas(1);
        Estado tres = envios.enviar(new Orden("envio-3", ana, "otro-banco", "cuenta-de-marta", 10_000_000));
        comprobar("el envío queda abierto: debitado y sin cerrar", tres == Estado.DEBITADO);
        comprobar("el dinero salió de la cuenta de Ana", saldo(banco, ana) == 70_000_000);
        comprobar("y sí llegó al otro banco, aunque aquí no se sepa", red.recibidoCentavos() == 30_000_000);
        comprobar("no se devolvió: habría quedado en los dos lados", saldo(banco, ana) + red.recibidoCentavos() == 100_000_000);
        comprobar("al reanudar, se cierra", envios.reanudarPendientes() == 1);
        comprobar("y el otro banco no abonó dos veces", red.recibidoCentavos() == 30_000_000);

        System.out.println("4. Nuestro proceso muere después de debitar y antes de llamar");
        red.matarElProceso(1);
        try {
            envios.enviar(new Orden("envio-4", ana, "otro-banco", "cuenta-de-marta", 10_000_000));
            comprobar("el proceso debía morir", false);
        } catch (IllegalStateException caida) {
            comprobar("la llamada no llegó a hacerse", red.recibidoCentavos() == 30_000_000);
        }
        comprobar("el débito quedó confirmado junto con la saga", saldo(banco, ana) == 60_000_000);
        comprobar("al arrancar de nuevo, la saga se retoma y se completa", envios.reanudarPendientes() == 1);
        comprobar("ahora sí llegó", red.recibidoCentavos() == 40_000_000);

        System.out.println("5. El cliente repite una orden que ya se completó");
        int llamadasAntes = red.llamadas();
        Estado repetida = envios.enviar(new Orden("envio-1", ana, "otro-banco", "cuenta-de-marta", 20_000_000));
        comprobar("responde con el estado que tenía", repetida == Estado.COMPLETADO);
        comprobar("sin debitar de nuevo ni llamar a la red", saldo(banco, ana) == 60_000_000 && red.llamadas() == llamadasAntes);

        System.out.println("6. Una regla del dominio corta la saga antes de empezar");
        try {
            envios.enviar(new Orden("envio-5", ana, "otro-banco", "cuenta-de-marta", 30_000_000));
            comprobar("debía rechazarse", false);
        } catch (ReglaDeNegocioViolada rechazo) {
            System.out.println("     " + rechazo.getMessage());
            comprobar("no queda ninguna saga abierta", envios.reanudarPendientes() == 0);
        }

        System.out.println("7. La invariante");
        comprobar("lo que tiene Ana más lo que recibió el otro banco es lo que había al principio",
                saldo(banco, ana) + red.recibidoCentavos() == 100_000_000);

        System.out.println(fallos == 0 ? "Todo correcto" : fallos + " comprobaciones fallaron");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static long saldo(ServicioDeCuentas banco, String cuentaId) {
        return banco.porId(cuentaId).orElseThrow().saldoCentavos();
    }

    private static void comprobar(String que, boolean cumple) {
        System.out.println((cumple ? "   ok    " : "   FALLA ") + que);
        if (!cumple) {
            fallos++;
        }
    }
}
```

Desde `ejemplo/`:

```bash
javac -Xlint:all -Werror -d salida -sourcepath ../../../../hexagonal/proyecto/src $(find src -name '*.java')
java -cp salida Main
```

### Análisis

| Paso | Qué demuestra |
|---|---|
| 1 | El camino feliz son dos transacciones locales con una llamada en medio |
| 2 | La compensación es una operación del negocio y deja rastro |
| 3 | Sin respuesta, la saga espera. Devolver aquí habría duplicado el dinero |
| 3 y 5 | La idempotencia hace falta en los dos extremos: en el otro banco y en la entrada de la saga |
| 4 | El estado guardado con el débito es lo que permite retomar tras una caída |
| 6 | Las reglas siguen en el dominio: el orquestador no sabe qué es un tope |
| 7 | La invariante se cumple al final, no en cada instante: eso es consistencia eventual |

El paso 6 rechaza el envío por el tope diario, no por saldo: Ana ha enviado 550.000 pesos hoy contando el envío que se devolvió. Es el efecto que la compensación no revierte.

### Lo que este ejemplo simplifica

- Usa un solo hilo. Con varias instancias reanudando a la vez, dos pueden tomar la misma saga: hace falta que el paso 3 sea seguro ante eso (aquí lo es, porque relee el estado) y conviene que el otro banco deduplique (aquí lo hace).
- No reintenta los conflictos de concurrencia de la [sesión 05 del módulo hexagonal](../../../hexagonal/05-concurrencia-y-aislamiento/).
- No tiene tiempo límite: una saga podría quedarse abierta para siempre. La [sesión 04](../04-cuando-todo-falla/) trata eso.

## Errores comunes

- Llamar al servicio remoto con la transacción local abierta.
- Guardar el estado de la saga en un sitio distinto de los datos locales: una caída entre los dos los deja en desacuerdo.
- Tratar la falta de respuesta como un rechazo y compensar.
- Reintentar sin que el otro lado sea idempotente.
- Poner reglas del negocio en el orquestador porque "ahí se ve todo el flujo".
- Un orquestador que conoce Kafka o HTTP directamente en lugar de un puerto.
- Compensaciones que intentan borrar en lugar de registrar una operación inversa.
- No tener nada que retome las sagas abiertas.

## Taller

**Objetivo:** construir la saga de compra de la tienda dentro de tu servicio de pedidos hexagonal, con el orquestador como servicio de aplicación y cada participante detrás de un puerto.

Sobre tu servicio de pedidos del [módulo hexagonal](../../../hexagonal/) y el diseño de la saga que hiciste en la [sesión 01](../01-transacciones-entre-servicios/).

1. Define el estado de la saga de compra como una entidad con transiciones protegidas. Escribe sus pruebas: qué transiciones son válidas y cuáles no.
2. Define un puerto de salida por participante: pagos, inventario y envíos. Cada operación recibe el identificador de la saga.
3. Escribe el orquestador como servicio de aplicación. Marca en un comentario dónde empieza y dónde termina cada transacción local, y comprueba que ninguna llamada remota queda dentro.
4. Haz que el repositorio de sagas confirme y revierta junto con los pedidos. Demuéstralo con una prueba que provoque un fallo después de guardar la saga.
5. Escribe dobles de los tres participantes con fallos programables: rechazo, respuesta perdida y caída del proceso.
6. Prueba, con esos dobles, cada camino: todo bien; pago rechazado; sin existencias después de cobrar; envío imposible después de cobrar y reservar. En cada uno, comprueba qué compensaciones se ejecutaron y en qué orden.
7. Prueba la respuesta perdida en cada participante. En ningún caso debe compensarse sin saber.
8. Escribe el puerto de entrada que reanuda las sagas abiertas y pruébalo tras una caída en cada punto.
9. Define la invariante de tu tienda (por ejemplo: todo pedido cobrado está enviado o reembolsado) y escribe una prueba que la compruebe al final de un escenario con fallos mezclados.
10. Sustituye el doble de pagos por un adaptador real, HTTP o Kafka, sin tocar el orquestador. Anota qué archivos cambiaste.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Ninguna llamada remota ocurre con una transacción abierta | Lectura del orquestador y sus comentarios |
| El estado de la saga se confirma junto con el cambio local | La prueba del paso 4 |
| Una respuesta perdida nunca dispara una compensación | Las pruebas del paso 7 |
| La saga se retoma tras una caída en cualquier punto | Las pruebas del paso 8 |
| El orquestador no importa ninguna tecnología | Búsqueda de `import` |
| La invariante se cumple con fallos mezclados | La prueba del paso 9 |

**Entrega:** rama `taller/sagas-06`, con el servicio en `taller/solucion/` y `ANALISIS.md` con la tabla de caminos y compensaciones del paso 6.

## En el trabajo real

Esta es la forma de una saga orquestada en un servicio Spring: el orquestador es un servicio de aplicación sin anotaciones de mensajería, cada participante es un puerto cuyo adaptador publica una orden en Kafka y cuya respuesta llega por un consumidor que llama a otro puerto de entrada, y una tarea programada reanuda lo pendiente. Cuando el número de sagas y de pasos crece, esa mecánica se delega en un motor de flujos; la separación entre reglas, coordinación y transporte sigue siendo la misma.

Sigue con el [taller integrador](../07-taller-integrador/).

## Preguntas senior

1. ¿En qué capa vive el orquestador de una saga y qué no debe contener?
2. ¿Por qué el estado de la saga se guarda en la misma transacción que el cambio local?
3. ¿Qué haces cuando un participante no responde, y por qué no compensas?
4. ¿Qué diferencia hay entre compensar y revertir?
5. ¿Dónde hace falta idempotencia en una saga?
6. ¿Qué invariante puedes prometer y en qué momento se cumple?
