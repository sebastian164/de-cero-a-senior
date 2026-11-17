# 01 — Especificar antes de construir

SDD es *spec-driven development*, desarrollo guiado por especificaciones. La idea es vieja; el nombre y las herramientas tomaron fuerza desde 2025, cuando se volvió normal que un asistente de IA escriba buena parte del código. Esta parte de la ruta no depende de ninguna herramienta concreta: las especificaciones son archivos de texto.

## Idea

Los planos de una casa. Con un albañil de confianza que vive en el barrio, puedes señalar con el dedo y decir "aquí una ventana": él pregunta, tú corriges sobre la marcha. Con una cuadrilla que trabaja rapidísimo, no pregunta nada y construye exactamente lo que entendió, señalar con el dedo produce en una tarde una casa entera que no es la que querías. Cuanto más rápido y más literal es quien construye, más vale el plano. Un asistente de IA es esa cuadrilla.

## Lo esencial

### Qué cambia

| Sin especificación | Con especificación |
|---|---|
| La intención vive en la conversación y en tu cabeza | La intención está escrita, versionada y revisada |
| El código es la única fuente de verdad | La especificación dice **qué** debe pasar; el código es una forma de lograrlo |
| Se revisa el código para adivinar si hace lo que se quería | Se revisa primero si lo que se quiere está bien definido |
| El error de entendimiento se descubre al final | Se descubre antes de escribir una línea |

### Niveles

| Nivel | Qué significa |
|---|---|
| Especificación primero | Se escribe antes de programar y luego se abandona |
| Especificación anclada | Se mantiene viva: cada cambio empieza por ella |
| Especificación como fuente | El código se regenera a partir de ella. Hoy es más una aspiración que una práctica |

Cuando alguien diga "hacemos SDD", pregunta cuál de los tres.

### Qué lleva una especificación

| Sección | Contenido |
|---|---|
| **Propósito** | Qué problema resuelve y para quién. Dos o tres frases |
| **Historias de usuario** | Quién quiere qué y para qué |
| **Criterios de aceptación** | Lo que debe cumplirse, redactado de forma que se pueda comprobar |
| **Casos límite y errores** | Qué pasa con lo vacío, lo enorme, lo repetido, lo no permitido |
| **Fuera de alcance** | Lo que explícitamente **no** se va a hacer |
| **Restricciones** | Límites que no son negociables: compatibilidad, seguridad, rendimiento |
| **Preguntas abiertas** | Lo que aún no se sabe. Mejor escrito que supuesto |

### Qué **no** lleva

Cómo se va a construir: clases, tablas, bibliotecas, nombres de métodos. Eso es el **plan**, que viene después y puede cambiar sin que cambie lo que el usuario recibe.

### Criterios de aceptación que se pueden comprobar

Una forma muy usada es la notación EARS, que obliga a decir el disparador y la respuesta:

| Patrón | Forma |
|---|---|
| Siempre | El sistema **deberá** ‹respuesta› |
| Ante un evento | **Cuando** ‹disparador›, el sistema **deberá** ‹respuesta› |
| En un estado | **Mientras** ‹estado›, el sistema **deberá** ‹respuesta› |
| Ante algo no deseado | **Si** ‹condición›, **entonces** el sistema **deberá** ‹respuesta› |
| Opcional | **Donde** ‹característica presente›, el sistema **deberá** ‹respuesta› |

La prueba de un buen criterio: dos personas que lo lean, por separado, escribirían la misma prueba.

## Ejemplo

Añadir búsqueda a las notas de [Bitácora](../../../docker-kubernetes/app/).

### Antes

> "Agrégale búsqueda a las notas."

### Análisis

Lo que esa frase no dice, y que alguien va a decidir por ti:

| Pregunta sin responder | Decisiones posibles |
|---|---|
| ¿Dónde se busca? | ¿Una ruta nueva, un parámetro en la que existe? |
| ¿Distingue mayúsculas y tildes? | "café" y "CAFE", ¿coinciden? |
| ¿Coincidencia parcial? | "pan", ¿encuentra "panadería"? |
| ¿Qué pasa con una búsqueda vacía? | ¿Todo, nada, error? |
| ¿Y si no hay resultados? | ¿Lista vacía con 200 o un 404? |
| ¿Hay límite de resultados? | Con cien mil notas, ¿devuelve todas? |
| ¿Cambia lo que ya existe? | ¿Los clientes actuales se rompen? |
| ¿Exige la clave? | Leer notas hoy no la pide |

Un asistente de IA responderá a las ocho sin preguntarte, con seguridad total, y quizá distinto cada vez que se lo pidas.

### Después

<!-- file: ejemplo/especificacion.md -->
```markdown
# Especificación: búsqueda de notas

## Propósito

Quien usa la bitácora acumula cientos de notas y hoy solo puede leerlas todas. Necesita encontrar
las que mencionan una palabra.

## Historias de usuario

- Como persona que usa la bitácora, quiero buscar notas por una palabra, para encontrar una nota
  sin leerlas todas.

## Criterios de aceptación

1. Cuando se consulta `GET /api/notas` sin el parámetro `q`, el sistema deberá responder exactamente
   como hoy.
2. Cuando se consulta `GET /api/notas?q=<texto>`, el sistema deberá devolver solo las notas que
   contienen `<texto>`.
3. El sistema deberá tratar como iguales las mayúsculas y las minúsculas, y las vocales con y sin
   tilde: `cafe` encuentra "Café" y "CAFÉ".
4. El sistema deberá aceptar coincidencias parciales: `pan` encuentra "panadería".
5. El sistema deberá devolver las notas en el mismo orden en que se guardaron.
6. Cuando ninguna nota coincide, el sistema deberá responder 200 con una lista vacía.
7. Si `q` está vacío o contiene solo espacios, entonces el sistema deberá responder como si `q` no
   se hubiera enviado.
8. Si `q` tiene más de 100 caracteres, entonces el sistema deberá responder 400 con un cuerpo
   `{ "error": "<mensaje>" }`.
9. El sistema deberá responder con la misma forma de hoy: `{ "host": <texto>, "notas": [<texto>…] }`.

## Casos límite

- `q` con caracteres especiales de expresiones regulares (`.`, `*`, `(`) se busca como texto literal.
- `q` con espacios al principio o al final se busca sin ellos.
- Sin notas guardadas, cualquier búsqueda devuelve una lista vacía.

## Fuera de alcance

- Buscar varias palabras a la vez o con operadores.
- Ordenar por relevancia.
- Paginación.
- Resaltar la coincidencia.
- Cambios en la página web.

## Restricciones

- No se añaden dependencias: la API sigue siendo un solo archivo que usa solo el JDK.
- La búsqueda no exige la clave, igual que la lectura actual.
- Ningún cliente existente debe notar el cambio.

## Preguntas abiertas

- ¿Hace falta un límite de resultados antes de que exista la paginación? Hoy no hay límite al listar.
```

- Las ocho preguntas tienen respuesta, o están anotadas como abiertas.
- Cada criterio se puede convertir en una prueba. El 3 y el 4 son casi escenarios de Karate tal cual.
- "Fuera de alcance" evita que alguien, persona o máquina, añada paginación "ya que estaba".
- No dice cómo se implementa.

## Errores comunes

- Meter el diseño técnico en la especificación.
- Criterios que no se pueden comprobar: "debe ser rápido", "debe ser fácil de usar".
- Omitir "fuera de alcance".
- Esconder las dudas en lugar de anotarlas como preguntas abiertas.
- Especificaciones de veinte páginas que nadie lee completas, tampoco quien las aprueba.
- Escribirla y no enseñársela a nadie antes de construir.
- Aceptar sin leer la especificación que generó un asistente a partir de una frase.

## Taller

**Objetivo:** escribir una especificación que dos personas implementarían igual.

**La funcionalidad:** etiquetas para las notas de Bitácora. Alguien quiere poder marcar una nota con etiquetas como `#trabajo` y luego ver solo las de una etiqueta.

1. Antes de escribir nada, lista todas las preguntas que la frase anterior deja sin responder. Apunta a quince o más.
2. Responde las que puedas decidir tú. Las que dependan del negocio, anótalas como abiertas y propón una respuesta por defecto.
3. Escribe la especificación con todas las secciones. Criterios en notación EARS.
4. Revisa cada criterio: ¿se puede escribir una prueba que lo compruebe sin ambigüedad? Reescribe los que no.
5. Escribe "fuera de alcance" con al menos cinco cosas que alguien podría querer añadir.
6. Asegúrate de que no hay ni una decisión de implementación. Si encuentras alguna, muévela a un archivo aparte de notas para el plan.
7. **La prueba de las dos personas.** Dale la especificación a dos personas, o a dos asistentes de IA en conversaciones separadas, y pídeles solo que escriban los casos de prueba. Compara: cada diferencia es una ambigüedad de tu texto. Corrígela.
8. Pídele a un asistente que busque contradicciones y huecos. ¿Encontró algo real? ¿Inventó problemas que no lo eran?
9. Convierte tres criterios en escenarios de Karate, todavía sin implementar nada. ¿Te obligaron a precisar algo más?
10. Compara tu especificación final con tu lista del paso 1. ¿Qué preguntas no habías visto al principio?

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Todos los criterios son comprobables | Cada uno tiene al menos un caso de prueba derivado |
| No hay decisiones de implementación | Lectura |
| La prueba de las dos personas está documentada | Las diferencias y cómo las resolviste |
| Hay preguntas abiertas con respuesta por defecto | Lectura |

**Entrega:** rama `taller/sdd-01`, con `taller/solucion/especificacion.md` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Esto no es nuevo: es lo que siempre hizo un buen análisis de requisitos. Lo nuevo es el incentivo. Antes, una especificación floja se compensaba conversando con quien programaba. Con un asistente que genera quinientas líneas en un minuto, la calidad de lo que escribes antes decide casi todo lo que viene después.

## Preguntas senior

1. ¿Qué diferencia hay entre la especificación y el plan?
2. ¿Qué hace comprobable a un criterio de aceptación?
3. ¿Para qué sirve la sección "fuera de alcance"?
4. ¿En qué se parece y en qué se diferencia SDD de TDD y de BDD?
5. ¿Por qué SDD tomó fuerza con los asistentes de IA?
