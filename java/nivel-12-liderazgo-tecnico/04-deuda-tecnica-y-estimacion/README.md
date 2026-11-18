# 04 — Deuda técnica, priorización y estimación

## Idea

Un préstamo permite comprar hoy lo que no se puede pagar de contado. Bien usado, es una herramienta; mal usado, los intereses se comen el ingreso. La deuda técnica funciona igual: un atajo de hoy se paga con intereses cada vez que se toca ese código. El problema no es endeudarse, sino no saber cuánto se debe.

## Lo esencial

### Tipos de deuda

| | Prudente | Imprudente |
|---|---|---|
| **Deliberada** | "Entregamos así para llegar a la fecha y lo corregimos después" | "No tenemos tiempo para diseñar" |
| **Inadvertida** | "Ahora sabemos cómo debimos hacerlo" | "¿Qué son las capas?" |

La deliberada y prudente es una decisión de negocio legítima, siempre que quede registrada y tenga plan de pago. La imprudente es simplemente mal trabajo.

No es deuda: una funcionalidad que falta, un error, o código que no te gusta. Es deuda lo que **hace más caro el cambio futuro**.

### Cómo se hace visible

- Un registro de deuda: qué es, dónde está, cuánto cuesta convivir con ella (el "interés") y cuánto cuesta arreglarla (el "capital").
- Señales medibles: tiempo para entregar un cambio, tasa de defectos, archivos que se modifican en casi todos los cambios, áreas que nadie quiere tocar.

### Cómo se prioriza

No toda deuda merece pagarse. La que vale la pena es la que tiene **interés alto**: código malo que se modifica a menudo. Código malo que nadie toca cuesta poco.

| | Se toca poco | Se toca mucho |
|---|---|---|
| **Fácil de arreglar** | Cuando se pase por ahí | Ya |
| **Difícil de arreglar** | Dejarlo | Planificar e invertir |

### Cómo se paga

- **De forma continua,** como parte del trabajo normal: mejorar lo que se toca.
- **Con capacidad reservada:** un porcentaje fijo de cada iteración.
- **Ligada a una funcionalidad:** "para construir esto bien, primero hay que ordenar aquello".
- **No** con una "iteración de refactorización" de meses sin entregar valor: es difícil de justificar y suele cancelarse.

### Cómo se explica al negocio

"Refactorizar" y "deuda técnica" no convencen a quien no programa. Lo que sí funciona es traducir a tiempo, dinero y riesgo:

- Mal: "necesitamos refactorizar el módulo de facturación".
- Bien: "hoy cada cambio de tarifas nos toma tres semanas y en los dos últimos hubo errores en producción. Con cuatro semanas de trabajo, los próximos cambios tomarían tres días. Hay cuatro cambios de tarifas previstos este año".

### Estimación

Estimar es predecir con incertidumbre. No es comprometerse a una fecha.

**Por qué las estimaciones fallan:** se estima el caso ideal, se olvidan pruebas, revisión, despliegue e integración, aparece lo imprevisto, y hay presión para dar el número que se quiere oír.

**Prácticas que ayudan:**

- **Rangos, no puntos:** "entre dos y cuatro semanas, probablemente tres".
- **Descomponer:** lo pequeño se estima mejor.
- **Comparar con trabajo anterior** en lugar de calcular desde cero.
- **Tres valores:** optimista, probable y pesimista.
- **Declarar los supuestos y los riesgos** que moverían la estimación.
- **Actualizarla** a medida que se aprende.
- **Reducir la incertidumbre primero:** un *spike* antes de estimar lo desconocido.
- **Cortar alcance, no calidad,** cuando la fecha es fija.

**Estimación frente a compromiso.** Si te piden una fecha, pregunta para qué se necesita. A menudo lo que se requiere es saber si algo cabe en el trimestre, y una respuesta por órdenes de magnitud basta.

## Ejemplo

Un registro de deuda:

| Deuda | Dónde | Interés (costo de no arreglar) | Capital | Prioridad |
|---|---|---|---|---|
| Servicio de pedidos sin pruebas | `pedidos` | Cada cambio exige dos días de pruebas manuales; tres incidentes este año | 3 semanas | Alta |
| Biblioteca de reportes obsoleta | `reportes` | Ninguno por ahora; riesgo de seguridad a futuro | 1 semana | Media, con fecha |
| Nombres inconsistentes | `legado.util` | Casi nulo: no se modifica | 2 días | No pagar |

## Errores comunes

- Llamar deuda técnica a todo lo que no gusta.
- Deuda deliberada sin registro ni plan.
- Pedir "tiempo para refactorizar" sin traducirlo a impacto.
- Reescribir desde cero como forma de pagar deuda.
- Dar una estimación de un solo número y sin supuestos.
- Ceder a la presión y prometer lo imposible.
- Reducir la calidad para cumplir una fecha, generando más deuda.
- No actualizar una estimación cuando ya se sabe que no se cumplirá. Las malas noticias se dan pronto.

## Taller

1. Construye el registro de deuda de un sistema que conozcas: al menos ocho elementos con su interés y su capital.
2. Clasifícalos en la matriz de frecuencia y dificultad y decide cuáles no vas a pagar.
3. Elige el más importante y redacta la propuesta para alguien de negocio: sin jerga, con tiempo, costo y riesgo.
4. Estima una funcionalidad real: descompón, da tres valores por parte, lista supuestos y riesgos.
5. Compara tres estimaciones tuyas pasadas con lo que realmente tomaron. ¿En qué te equivocas de forma sistemática?
6. Redacta el mensaje para avisar a tiempo que una fecha no se va a cumplir, con opciones.

Rama `taller/n12-04-deuda-estimacion`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué deuda técnica vale la pena pagar y cuál no?
2. ¿Cómo convences a producto de invertir en algo que el usuario no ve?
3. ¿Reescribir o refactorizar?
4. Te piden una fecha para algo que no has hecho nunca. ¿Qué respondes?
5. Vas a incumplir una fecha. ¿Qué haces y cuándo?
6. ¿Cómo equilibras entregar rápido con mantener la calidad?
