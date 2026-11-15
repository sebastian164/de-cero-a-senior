# 03 — Mantener la especificación viva, y cuándo no vale la pena

## Idea

El mapa de una ciudad. El día que se imprime es exacto. Luego abren una avenida, cierran un puente, y el mapa sigue diciendo lo mismo con total seguridad. Un mapa desactualizado es peor que no tener mapa, porque la gente le hace caso. Una especificación que ya no describe al sistema es ese mapa.

## Lo esencial

### La deriva

La especificación y el código se separan de formas previsibles:

| Cómo ocurre | Ejemplo |
|---|---|
| Arreglo urgente directo en el código | "Lo corrijo ya y luego actualizo el documento" |
| Decisión tomada durante la implementación | Se descubre un caso límite y se resuelve sin anotarlo |
| Cambio de requisitos conversado y no escrito | "Quedamos en que el límite era 200" |
| Especificación generada, aprobada sin leer | Nunca describió lo que se quería |

### La regla

**Todo cambio de comportamiento empieza por la especificación.** Primero se cambia el texto, se revisa, y después el código. Si el cambio fue urgente y se hizo al revés, actualizar la especificación forma parte de terminar, no es una tarea para después.

### Especificaciones que se ejecutan

La única especificación que no puede derivar en silencio es la que **falla** cuando el sistema deja de cumplirla. Cada criterio de aceptación con una prueba automática asociada (un escenario de Karate, una prueba unitaria) es un trozo de especificación ejecutable. La prosa explica el porqué; la prueba vigila el qué.

| | Solo prosa | Prosa y pruebas enlazadas |
|---|---|---|
| ¿Detecta la deriva? | Solo si alguien relee | Sí, en la integración continua |
| ¿Explica la intención? | Sí | Sí |
| ¿Cuesta mantenerla? | Poco, hasta que miente | Más, y dice la verdad |

### En la revisión de código

Un cambio de comportamiento sin cambio en la especificación es una señal de alarma para quien revisa, igual que un cambio sin pruebas.

### Lo que se critica, y con razón

| Crítica | Qué hay de cierto |
|---|---|
| "Es cascada con otro nombre" | Si especificas todo el producto por adelantado, sí. Funciona por funcionalidad pequeña, en ciclos cortos |
| "Genera montañas de texto que nadie lee" | Ocurre cuando se acepta todo lo que produce la herramienta. Una especificación que no cabe en una pantalla o dos es sospechosa |
| "Especificar bien es tan difícil como programar" | Cierto. Lo que cambia es dónde se piensa, no cuánto |
| "La especificación deriva" | Sí, si no hay pruebas enlazadas ni la regla anterior |
| "Da una falsa sensación de control" | Sí, cuando las puertas se aprueban sin leer |

### Cuándo no usarlo

| Situación | Por qué | En su lugar |
|---|---|---|
| Arreglar un error pequeño y claro | La especificación es más larga que el arreglo | Una prueba que lo reproduzca |
| Explorar: no sabes qué quieres | No se puede especificar lo que no se entiende | Un experimento desechable; especificar después |
| Un cambio puramente interno (refactor) | No cambia el comportamiento | Las pruebas existentes |
| Un guion de un solo uso | No se va a mantener | Nada |

Rinde más cuando el trabajo es una funcionalidad con reglas, va a vivir tiempo, la toca más de una persona o la va a escribir un asistente.

## Ejemplo

### Antes

Tres meses después de entregar la búsqueda: un cliente pidió que también buscara por fecha y alguien lo añadió directo en el código. Luego se subió el límite de `q` de 100 a 200 caracteres por un caso real. La especificación sigue diciendo 100 y no menciona fechas. Llega alguien nuevo, lee la especificación, "corrige" el límite a 100 creyendo que era un error, y rompe al cliente que lo necesitaba.

### Análisis

| Problema | Consecuencia |
|---|---|
| Dos cambios de comportamiento sin tocar la especificación | El documento miente, y lo hace con autoridad |
| Ninguna prueba enlazada al criterio del límite | Nada falló cuando se cambió a 200 ni cuando se devolvió a 100 |
| La razón del cambio no quedó en ninguna parte | La persona nueva no tenía forma de saber que 200 era intencional |

### Después

El mismo cambio, hecho en orden. Primero, el texto:

<!-- file: ejemplo/cambio.md -->
```markdown
# Cambio 002: ampliar el límite de la consulta

## Motivo

Un cliente busca por referencias de pedido que llegan a 160 caracteres. Con el límite de 100 recibe un 400.

## Cambio en la especificación

- Criterio 8, antes: "Si `q` tiene más de 100 caracteres, entonces el sistema deberá responder 400…"
- Criterio 8, ahora: "Si `q` tiene más de 200 caracteres, entonces el sistema deberá responder 400…"

## Lo que no cambia

Todo lo demás. En particular, la forma del error.

## Pruebas afectadas

- `busqueda.feature`, escenario "rechaza una consulta demasiado larga": los límites pasan de 100/101 a 200/201.

## Riesgo

Ninguno para clientes existentes: se acepta más, no menos.
```

Después, en este orden: se actualiza el criterio en la especificación, se cambia la prueba (que pasa a fallar), se cambia el código y la prueba vuelve a pasar. Todo en un mismo pull request, de modo que quien revisa ve las cuatro cosas juntas.

- El motivo queda escrito.
- La prueba enlazada al criterio impide que alguien lo "corrija" sin enterarse.
- Quien llegue nuevo lee una especificación que describe el sistema.

## Errores comunes

- Tratar la especificación como un entregable que se archiva.
- "Luego actualizo el documento."
- Criterios sin prueba asociada.
- Aplicar el proceso completo a un cambio de una línea.
- No aplicarlo nunca "porque es burocracia".
- Especificaciones tan largas que actualizarlas da pereza.
- Confiar en que la herramienta mantiene todo sincronizado sin que nadie lea.

## Taller integrador de SDD

**Objetivo:** hacer evolucionar tu funcionalidad de etiquetas sin que la especificación se separe del código, y decidir con criterio cuándo usar SDD.

1. **Enlaza.** Para cada criterio de tu especificación, anota junto a él qué prueba lo vigila. Los que no tengan, o reciben una prueba o una explicación de por qué no se puede automatizar.
2. **Un cambio en orden.** Llega una petición: "una nota puede tener como máximo cinco etiquetas". Escribe el documento de cambio, actualiza la especificación, cambia las pruebas, velas fallar, cambia el código. Un solo pull request.
3. **Un cambio al revés.** Ahora haz uno mal a propósito: cambia el comportamiento directo en el código (por ejemplo, que las etiquetas no distingan mayúsculas). ¿Qué te avisó de la deriva? Si nada, añade lo que falta para que la próxima vez sí avise.
4. **Una urgencia.** Simula un arreglo de producción: tienes diez minutos para corregir un error. Hazlo. Después, escribe qué debe ocurrir antes de dar por cerrada la urgencia.
5. **Detección de deriva.** Pídele a un asistente, o hazlo a mano, que compare la especificación con el código y liste las diferencias. ¿Encontró las del paso 3? ¿Inventó diferencias falsas?
6. **Poda.** Relee tu especificación completa. ¿Qué sobra? Recórtala hasta que quepa en dos pantallas sin perder ningún criterio.
7. **Cuándo no.** Elige tres cambios reales de un proyecto tuyo: uno en el que SDD habría ayudado, uno en el que habría estorbado y uno dudoso. Justifica cada uno.
8. **El costo.** Con tus tiempos de la sesión 02, estima cuánto añadió el proceso y qué errores evitó. ¿A partir de qué tamaño de cambio se paga solo?
9. **La guía del equipo.** Escribe, en una página, cómo propondrías usar SDD en un equipo: para qué cambios, qué secciones son obligatorias, qué se revisa en cada puerta, cómo se enlazan criterios y pruebas, y qué hacer en una urgencia.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Cada criterio tiene prueba o justificación | La especificación anotada |
| El cambio del paso 2 está en un solo pull request, en orden | El historial |
| La deriva del paso 3 se detecta de forma automática | Una prueba que falla |
| La guía dice cuándo **no** usar SDD | Lectura |

**Entrega:** rama `taller/sdd-03`, con todo en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

Las herramientas de esta área cambian cada pocos meses y es probable que las de hoy no sean las de dentro de dos años. Lo que queda es el oficio: escribir qué se quiere de forma que se pueda comprobar, decidir el cómo a la vista de otros, trabajar en pasos pequeños y no dejar que el texto y el sistema se separen. Eso sirve con cualquier asistente y también sin ninguno.

## Preguntas senior

1. ¿Cómo evitas que la especificación y el código se separen?
2. ¿Qué es una especificación ejecutable?
3. ¿Qué críticas a SDD te parecen válidas y cómo las mitigas?
4. ¿Cuándo no usarías SDD?
5. Tu equipo quiere adoptar SDD para todo. ¿Qué propones?
