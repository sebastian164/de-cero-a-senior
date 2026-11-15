# 01 — Preguntas antes que código

ODD es *observability-driven development*, desarrollo guiado por la observabilidad: decidir **antes de escribir el código** qué vas a necesitar saber cuando esté en producción, e instrumentarlo como parte de la funcionalidad, no después del primer incidente.

## Idea

El tablero de un carro. Nadie diseña el motor, lo vende y, cuando se funde el primero, decide que habría sido buena idea ponerle un indicador de temperatura. El tablero se diseña con el motor, respondiendo a una pregunta: ¿qué necesita saber el conductor para no quedarse tirado? Tu código en producción es ese motor, y la mayoría sale de fábrica sin tablero.

## Lo esencial

### Monitoreo y observabilidad

| | Monitoreo | Observabilidad |
|---|---|---|
| Responde | Preguntas que previste: "¿está arriba?", "¿cuánta CPU usa?" | Preguntas que **no** previste: "¿por qué los pagos de este banco tardan el triple desde ayer?" |
| Se basa en | Tableros y umbrales fijos | Datos lo bastante ricos para explorarlos |
| Falla cuando | El problema es nuevo | Los datos no traen el contexto necesario |

Un sistema es observable cuando puedes entender qué le pasa por dentro mirando solo lo que emite, sin desplegar código nuevo para averiguarlo.

### El ciclo de ODD

| Paso | Pregunta |
|---|---|
| 1. Antes de programar | ¿Cómo sabré que esto funciona en producción? ¿Qué me preguntarán cuando falle? |
| 2. Al programar | Emitir lo necesario para responder, como parte del cambio |
| 3. Antes de fusionar | Comprobar, viendo la telemetría real, que las respuestas salen |
| 4. Después de desplegar | **Mirar.** Confirmar con los datos que se comporta como esperabas |

Se parece a TDD a propósito: en TDD escribes primero cómo sabrás que funciona en tu máquina; en ODD, cómo sabrás que funciona en producción.

### Las señales

| Señal | Qué es | Sirve para |
|---|---|---|
| **Registros** (*logs*) | Algo que ocurrió, con su contexto | El detalle de un caso |
| **Métricas** | Números agregados en el tiempo | Tendencias y alertas |
| **Trazas** | El recorrido de una petición por todos los servicios | Saber dónde se fue el tiempo |

Esta sesión se ocupa de la primera, bien hecha. Las otras dos son la sesión 02.

### Eventos estructurados

| Texto libre | Evento estructurado |
|---|---|
| `Error procesando el pago!!` | `{"evento":"pago.procesado","resultado":"error","motivo":"el banco no responde","medio":"transferencia","duracion_ms":812,"pedido":"p-3","correlacion":"c-7f3a"}` |
| Lo lee una persona, con paciencia | Lo consulta una máquina: filtrar, contar, agrupar |
| Tres líneas sueltas por operación | **Un** evento por operación, con todo lo que se supo |

Un evento ancho por unidad de trabajo —una petición, un mensaje procesado— con muchos campos, vale más que diez líneas de texto repartidas.

### Qué campos

| Campo | Para qué |
|---|---|
| Identificador de correlación | Unir todo lo que pertenece a la misma operación, entre servicios |
| Resultado | Éxito, rechazo de negocio o error técnico. **No son lo mismo** |
| Motivo | Por qué, en una forma que se pueda agrupar |
| Duración | Cuánto tardó |
| Dimensiones del negocio | Lo que te permitirá cortar los datos: medio de pago, tipo de cliente, versión |
| Versión del servicio | Saber si el problema llegó con un despliegue |

Distinguir "rechazado" de "error" importa: que una tarjeta no tenga fondos es el sistema funcionando bien; que el banco no responda es un problema tuyo. Si los dos son "error", tus alertas suenan cada vez que un cliente se queda sin saldo.

### Qué no se registra

Contraseñas, tokens, números de tarjeta, documentos y, en general, datos personales que no necesitas para operar. Los registros se copian a muchos sistemas y los lee mucha gente.

## Ejemplo

Java puro. Ejecuta cada uno con `java Main.java`.

### Antes

<!-- file: ejemplo/antes/Main.java -->
```java
/** Un servicio de pagos que "tiene logs". */
public class Main {

    public static void main(String[] args) {
        pagar("pedido-1", 800, "tarjeta");
        pagar("pedido-2", 5000, "tarjeta");
        pagar("pedido-3", 300, "transferencia");
        pagar("pedido-4", 450, "transferencia");
    }

    static void pagar(String pedido, int valor, String medio) {
        System.out.println("Iniciando pago");
        try {
            cobrar(valor, medio);
            System.out.println("Pago OK");
        } catch (RuntimeException error) {
            System.out.println("Error!!! " + error.getMessage());
        }
    }

    static void cobrar(int valor, String medio) {
        if (valor > 1000) {
            throw new IllegalArgumentException("fondos insuficientes");
        }
        if (medio.equals("transferencia")) {
            throw new IllegalStateException("el banco no responde");
        }
    }
}
```

### Análisis

Con esa salida, intenta responder:

| Pregunta | ¿Se puede? |
|---|---|
| ¿Qué le pasó al `pedido-2`? | No. Ninguna línea dice a qué pedido pertenece |
| ¿Qué porcentaje de los pagos falla por culpa nuestra? | No. El rechazo por fondos y la caída del banco son ambos "Error!!!" |
| ¿Fallan más las transferencias o las tarjetas? | No. El medio de pago no aparece |
| ¿Cuánto tardan los pagos? | No |
| ¿Empezó con el último despliegue? | No |

Tiene registros, y no es observable. Las tres líneas por pago ni siquiera se pueden unir entre sí cuando hay dos pagos a la vez.

### Después

Primero las preguntas (son las de la tabla). Después, el código que las responde:

<!-- file: ejemplo/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** El mismo servicio, instrumentado para responder preguntas: un evento ancho por pago. */
public class Main {

    static final List<Map<String, Object>> emitidos = new ArrayList<>();   // en un sistema real: el recolector

    public static void main(String[] args) {
        pagar("c-01", "pedido-1", 800, "tarjeta");
        pagar("c-02", "pedido-2", 5000, "tarjeta");
        pagar("c-03", "pedido-3", 300, "transferencia");
        pagar("c-04", "pedido-4", 450, "transferencia");

        System.out.println();
        System.out.println("¿Qué le pasó al pedido-2?");
        emitidos.stream().filter(e -> e.get("pedido").equals("pedido-2"))
                .forEach(e -> System.out.println("  " + e.get("resultado") + ": " + e.get("motivo")));

        long errores = emitidos.stream().filter(e -> e.get("resultado").equals("error")).count();
        System.out.println("¿Qué parte falla por culpa nuestra?");
        System.out.println("  " + errores + " de " + emitidos.size() + " (los rechazos de negocio no cuentan)");

        System.out.println("¿Qué medio de pago concentra los errores?");
        Map<Object, Long> porMedio = emitidos.stream().filter(e -> e.get("resultado").equals("error"))
                .collect(Collectors.groupingBy(e -> e.get("medio"), LinkedHashMap::new, Collectors.counting()));
        System.out.println("  " + porMedio);
    }

    static void pagar(String correlacion, String pedido, int valor, String medio) {
        long inicio = System.nanoTime();
        String resultado = "aprobado";
        String motivo = "";
        try {
            cobrar(valor, medio);
        } catch (IllegalArgumentException rechazo) {        // el sistema funcionó: el negocio dijo que no
            resultado = "rechazado";
            motivo = rechazo.getMessage();
        } catch (IllegalStateException fallo) {             // el sistema falló
            resultado = "error";
            motivo = fallo.getMessage();
        }

        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("evento", "pago.procesado");
        evento.put("correlacion", correlacion);
        evento.put("pedido", pedido);
        evento.put("medio", medio);
        evento.put("valor", valor);
        evento.put("resultado", resultado);
        evento.put("motivo", motivo);
        evento.put("duracion_ms", (System.nanoTime() - inicio) / 1_000_000);
        evento.put("version", "1.4.2");
        emitir(evento);
    }

    static void cobrar(int valor, String medio) {
        if (valor > 1000) {
            throw new IllegalArgumentException("fondos insuficientes");
        }
        if (medio.equals("transferencia")) {
            throw new IllegalStateException("el banco no responde");
        }
    }

    /** Una línea por evento, en JSON: lo que un recolector sabe leer. */
    static void emitir(Map<String, Object> evento) {
        emitidos.add(evento);
        String json = evento.entrySet().stream()
                .map(campo -> "\"" + campo.getKey() + "\":" + (campo.getValue() instanceof Number
                        ? campo.getValue().toString()
                        : "\"" + campo.getValue().toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""))
                .collect(Collectors.joining(",", "{", "}"));
        System.out.println(json);
    }
}
```

- Un evento por pago, con todo lo que se supo de él.
- Las tres preguntas se responden filtrando y agrupando. En producción harías lo mismo con el lenguaje de consulta de tu herramienta.
- El rechazo de negocio y el error técnico están separados: una alerta sobre `resultado=error` no sonará porque un cliente no tenga saldo.
- No hay número de tarjeta, ni nombre, ni documento.

En un proyecto real no escribirías el JSON a mano: usarías la biblioteca de registros con formato estructurado. El punto del ejemplo es **qué** se emite, no con qué.

## Errores comunes

- Instrumentar después del primer incidente.
- Texto libre que solo se puede buscar con expresiones regulares.
- Muchas líneas por operación, sin nada que las una.
- Tratar los rechazos de negocio como errores.
- Registrar datos personales o secretos.
- Registrar todo "por si acaso" y no poder encontrar nada.
- Campos con nombres distintos para lo mismo en cada servicio (`pedidoId`, `id_pedido`, `order`).
- Emitir y no mirar nunca.

## Taller

**Objetivo:** instrumentar la API de [Bitácora](../../../docker-kubernetes/app/) a partir de preguntas, no de costumbres.

Bitácora ya escribe una línea por petición. Tu punto de partida es `app/api/Servidor.java`; trabaja sobre una copia.

1. **Antes de tocar el código**, escribe diez preguntas que querrías poder responder con Bitácora en producción. Mezcla de operación ("¿cuántas peticiones fallan?") y de negocio ("¿cuántas notas se guardan por hora?"). Incluye al menos tres del tipo "¿por qué…?".
2. Con la salida actual de la API, ¿cuáles puedes responder? Marca cada una.
3. Diseña el evento por petición: lista de campos, con nombre, tipo y la pregunta a la que sirve. Un campo que no sirve a ninguna pregunta se quita.
4. Impleméntalo: una línea JSON por petición.
5. Añade el identificador de correlación: si la petición trae la cabecera `X-Correlacion`, se usa; si no, se genera. Debe volver en la respuesta. Haz que el nginx de la web lo genere y lo envíe.
6. Distingue los resultados: éxito, rechazo del cliente (400, 401, 404, 405) y error del servidor. ¿En cuál de los tres cae un 503 por no estar listo?
7. Revisa qué estás registrando. ¿El contenido de las notas debería ir en el registro? ¿Y la clave? Decide y justifica.
8. Genera tráfico variado y responde tus diez preguntas con la salida, usando `jq` o un guion corto. Las que no puedas responder todavía, ¿qué campo les falta?
9. Añade un evento de negocio distinto del de petición: `nota.guardada`. ¿Qué preguntas responde que el de petición no?
10. Introduce un defecto a propósito (por ejemplo, las notas de más de 400 caracteres tardan dos segundos). Dale la salida a un compañero, **sin el código**, y pídele que encuentre qué pasa. Si no puede, falta instrumentación.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Las preguntas se escribieron antes que el código | El historial |
| Cada campo del evento sirve a una pregunta | La tabla del paso 3 |
| Las diez preguntas se responden con la salida | Paso 8, con las consultas usadas |
| No se registran secretos ni contenido sensible | Lectura de la salida |
| El defecto del paso 10 se encuentra sin leer el código | Paso 10 |

**Entrega:** rama `taller/odd-01`, con el código en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

La pregunta "¿cómo sabré que esto funciona en producción?" cabe en la plantilla de un pull request, y cambia la conversación de la revisión. Los equipos que la hacen en serio encuentran los problemas mirando sus datos el día del despliegue; los demás, cuando escribe un cliente.

## Preguntas senior

1. Diferencia entre monitoreo y observabilidad.
2. ¿Qué es ODD y en qué se parece a TDD?
3. ¿Por qué un evento ancho por operación y no muchas líneas?
4. ¿Por qué separar los rechazos de negocio de los errores técnicos?
5. ¿Qué no debe aparecer nunca en un registro?
