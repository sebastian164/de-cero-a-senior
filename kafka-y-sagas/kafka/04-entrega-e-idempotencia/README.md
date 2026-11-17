# 04 — Garantías de entrega e idempotencia

## Idea

El botón del ascensor. Lo pulsas una vez o diez, impaciente, y el resultado es el mismo: el ascensor viene una vez. Nadie diseñó el edificio suponiendo que la gente pulsaría exactamente una vez; se diseñó el botón para que repetir no hiciera daño. Con los mensajes ocurre igual: no puedes garantizar que lleguen una sola vez, pero sí que recibirlos dos veces dé el mismo resultado.

## Lo esencial

### Las tres garantías

| Garantía | Significa | Cómo se obtiene |
|---|---|---|
| Como mucho una vez | Puede perderse; nunca se repite | Confirmar antes de procesar |
| Al menos una vez | Nunca se pierde; puede repetirse | Confirmar después de procesar |
| Exactamente una vez | Ni se pierde ni se repite | Solo en condiciones muy concretas |

### "Exactamente una vez": lo que es y lo que no

Kafka ofrece **transacciones**: un productor puede escribir en varios temas y confirmar la posición de lectura como una sola operación atómica. Con eso, un proceso que lee de un tema, transforma y escribe en otro tema obtiene exactamente una vez **dentro de Kafka**.

En cuanto tu consumidor hace algo fuera de Kafka (escribe en una base de datos, llama a otro servicio, envía un correo), esa garantía ya no te cubre: Kafka no puede deshacer un correo. Para esos casos, que son la mayoría, la respuesta es otra:

> **Entrega al menos una vez + consumidor idempotente = efecto exactamente una vez.**

### Formas de ser idempotente

| Técnica | Cómo funciona | Cuándo |
|---|---|---|
| Operación idempotente por naturaleza | "Poner el estado en PAGADO" da igual hacerlo una vez o tres. "Sumar 100" no | Siempre que se pueda rediseñar el mensaje |
| Registro de mensajes procesados | Guardar el identificador del mensaje **en la misma transacción** que el efecto; si ya está, se ignora | Efectos en una base de datos propia |
| Inserción con clave única | La base rechaza el duplicado | Creación de registros |
| Control de versión | Aplicar solo si la versión del mensaje es mayor que la guardada | Actualizaciones que pueden llegar en desorden |
| Clave de idempotencia | Enviar el identificador al servicio externo para que él descarte repetidos | Llamadas a terceros, como pasarelas de pago |

El identificador tiene que venir **en el mensaje** y ser el mismo en cada reentrega. Un identificador generado al recibir no sirve.

### Mensajes que no se pueden procesar

| Situación | Qué hacer |
|---|---|
| Fallo pasajero (el servicio de al lado no responde) | Reintentar con espera creciente |
| Mensaje defectuoso (formato inválido, dato imposible): un "mensaje envenenado" | No reintentar para siempre: bloquea la partición. Enviarlo a un **tema de mensajes fallidos** con el motivo, y seguir |
| Muchos fallos seguidos | Detener el consumo y avisar: probablemente el problema no es el mensaje |

Un tema de mensajes fallidos que nadie mira es un cubo de basura con otro nombre: necesita alerta, dueño y una forma de reprocesar.

## Ejemplo

Java puro, sin Kafka. Ejecuta cada uno con `java Main.java`.

### Antes

<!-- file: ejemplo/antes/Main.java -->
```java
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Un consumidor que aplica cada mensaje sin comprobar si ya lo había aplicado. */
public class Main {

    record Abono(String id, String cuenta, int valor) {}

    static final Map<String, Integer> saldos = new HashMap<>();

    public static void main(String[] args) {
        Abono a1 = new Abono("abono-1", "cuenta-7", 100);
        Abono a2 = new Abono("abono-2", "cuenta-7", 50);
        Abono a3 = new Abono("abono-3", "cuenta-7", 25);

        // Lo que entrega el broker con "al menos una vez": abono-2 llega dos veces
        // porque el consumidor se reinició después de procesarlo y antes de confirmarlo.
        List<Abono> entregas = List.of(a1, a2, a2, a3);

        for (Abono abono : entregas) {
            aplicar(abono);
        }

        System.out.println();
        System.out.println("Saldo esperado: 175");
        System.out.println("Saldo real:     " + saldos.get("cuenta-7"));
    }

    static void aplicar(Abono abono) {
        saldos.merge(abono.cuenta(), abono.valor(), Integer::sum);
        System.out.println("aplicado " + abono.id() + " (+" + abono.valor() + ")");
    }
}
```

### Análisis

| Problema | Consecuencia |
|---|---|
| El efecto es "sumar", que no es idempotente | Cada repetición cambia el resultado |
| El consumidor supone que cada mensaje llega una vez | Esa suposición es falsa en cualquier sistema que confirme después de procesar |
| El error es de datos, no de ejecución | No hay excepción ni alerta. Se descubre cuando un cliente reclama |

### Después

<!-- file: ejemplo/despues/Main.java -->
```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Un consumidor idempotente: recuerda qué mensajes aplicó, junto con el efecto. */
public class Main {

    record Abono(String id, String cuenta, int valor) {}

    // En un sistema real, estas dos estructuras son dos tablas de LA MISMA base de datos
    // y se escriben en LA MISMA transacción. Si no, entre una y otra cabe una caída.
    static final Map<String, Integer> saldos = new HashMap<>();
    static final Set<String> mensajesAplicados = new HashSet<>();

    public static void main(String[] args) {
        Abono a1 = new Abono("abono-1", "cuenta-7", 100);
        Abono a2 = new Abono("abono-2", "cuenta-7", 50);
        Abono a3 = new Abono("abono-3", "cuenta-7", 25);

        List<Abono> entregas = List.of(a1, a2, a2, a3, a1);   // dos repeticiones

        for (Abono abono : entregas) {
            aplicar(abono);
        }

        System.out.println();
        System.out.println("Saldo esperado: 175");
        System.out.println("Saldo real:     " + saldos.get("cuenta-7"));
    }

    static void aplicar(Abono abono) {
        if (!mensajesAplicados.add(abono.id())) {             // el identificador viene en el mensaje
            System.out.println("repetido " + abono.id() + ": se ignora");
            return;
        }
        saldos.merge(abono.cuenta(), abono.valor(), Integer::sum);
        System.out.println("aplicado " + abono.id() + " (+" + abono.valor() + ")");
    }
}
```

El saldo es correcto aunque haya repeticiones. Lo que hace que funcione no es el `Set`: es que el identificador viaja con el mensaje y que el registro de "ya aplicado" se guarda de forma atómica con el efecto.

## Errores comunes

- Creer que "exactamente una vez" de Kafka cubre la base de datos o el correo.
- Guardar el identificador procesado en un sitio y el efecto en otro, sin transacción.
- Generar el identificador al consumir.
- Deduplicar en memoria: se olvida todo al reiniciar, justo cuando llegan las repeticiones.
- Reintentar para siempre un mensaje que nunca va a poder procesarse.
- Mandar a mensajes fallidos lo que era un fallo pasajero.
- Un tema de mensajes fallidos sin alerta ni dueño.
- Limpiar nunca el registro de procesados, o limpiarlo antes de que termine la retención del tema.

## Taller

**Objetivo:** construir un consumidor que dé el resultado correcto con repeticiones, mensajes defectuosos y fallos pasajeros.

**Sobre tu servicio hexagonal:** si hiciste el [módulo de arquitectura hexagonal](../../../hexagonal/), la deduplicación va en la **aplicación**, detrás de un puerto de salida que recuerda los eventos vistos, y se confirma en la misma unidad de trabajo que el efecto. El adaptador solo clasifica los fallos: reintentar, apartar o detenerse.

Necesitas el entorno, tu productor y una base de datos (PostgreSQL en un contenedor sirve). El caso: un tema `abonos` cuyos mensajes suman saldo a cuentas.

1. Escribe el consumidor ingenuo, que suma sin más. Produce cien abonos, mata el consumidor varias veces mientras procesa (confirmando después de procesar) y compara el saldo final con el esperado.
2. Hazlo idempotente con un registro de mensajes procesados en la misma transacción que el saldo. Repite la prueba: el saldo debe ser exacto.
3. Demuestra por qué hace falta la misma transacción: sepáralas, provoca una caída entre las dos escrituras y muestra el resultado.
4. Rediseña el mensaje para que la operación sea idempotente por naturaleza (por ejemplo, que lleve el saldo resultante y una versión). ¿Qué ganaste y qué perdiste? ¿Qué pasa ahora si dos mensajes llegan en desorden?
5. Publica a mano un mensaje con formato inválido en medio de mensajes buenos. Observa qué le pasa a tu consumidor y a los mensajes que vienen detrás **en esa partición** y en las demás.
6. Implementa un tema de mensajes fallidos: el mensaje defectuoso va allí con el motivo, el tema y la posición de origen en las cabeceras, y el consumo sigue.
7. Simula un fallo pasajero: la base rechaza las escrituras durante veinte segundos. Tu consumidor debe reintentar con espera creciente y **no** mandar esos mensajes al tema de fallidos. ¿Cómo distingue tu código un caso del otro?
8. Escribe una herramienta mínima que lea el tema de fallidos y republique en el original. ¿Qué pasa con el orden?
9. Implementa una canalización de lectura, transformación y escritura entre dos temas usando transacciones de Kafka. Mata el proceso a mitad y demuestra, leyendo con `isolation.level=read_committed`, que no hay duplicados en el tema de salida. Repite leyendo sin ese nivel y explica lo que ves.
10. Responde: ¿cuánto tiempo guardas el registro de mensajes procesados y de qué depende?

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Saldo exacto tras matar el consumidor repetidas veces | Comparación con el valor esperado |
| Un mensaje defectuoso no detiene el consumo | Paso 6 |
| Un fallo pasajero no llena el tema de fallidos | Paso 7 |
| Se entiende el límite de las transacciones de Kafka | Paso 9 y `ANALISIS.md` |

**Entrega:** rama `taller/kafka-04`, con el código en `taller/solucion/` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Casi todos los incidentes de datos en sistemas con mensajería son una de estas dos cosas: un consumidor que no era idempotente, o un mensaje envenenado que bloqueó una partición durante horas. Las dos se previenen en el diseño, y las dos se preguntan en una entrevista senior.

## Preguntas senior

1. ¿Por qué "exactamente una vez" no existe de extremo a extremo y cómo se consigue el mismo efecto?
2. ¿Qué hace idempotente a un consumidor? Da tres técnicas y cuándo usar cada una.
3. ¿Por qué el registro de procesados debe escribirse en la misma transacción que el efecto?
4. ¿Qué cubren las transacciones de Kafka y qué no?
5. ¿Cómo distingues un fallo pasajero de un mensaje envenenado, y qué haces con cada uno?
6. ¿Qué problemas trae reprocesar desde un tema de mensajes fallidos?
