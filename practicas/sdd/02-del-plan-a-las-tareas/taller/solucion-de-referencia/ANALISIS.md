# Taller SDD 02: del plan a las tareas

Cómo se trabajó: a mano, con archivos de texto, sin herramienta de SDD ni asistente aparte. Karate 2.1.4, JDK 21, Maven, git.
`./comprobar.sh` reconstruye el historial en una carpeta temporal, ejecuta cada tarea y comprueba los criterios. Archivos: `principios.md`, `aclaraciones.md`, `plan.md`, `tareas.md`, `codigo/`, `pruebas/`, `control/`; `tabla-verificacion.md` es la salida del paso 8, y `codigo/etapas/` guarda el servidor tal como queda al cerrar cada tarea.

## Evidencia de revisión en cada puerta (pasos 2 a 4)

| Puerta | Correcciones anotadas |
|---|---|
| Aclarar (paso 2) | 8 preguntas, 5 dudas reales. La 7 (`etiqueta` repetida) obligó a decidir algo que la especificación no decía; se anotó en el plan. La 2 (`#` sin codificar) se comprobó con curl: llega vacío, 400 |
| Planear (paso 3) | Revisado como un diseño: 7 decisiones, cada una con razón y alternativa; ninguna sin razón, ningún requisito inventado, respeta la restricción de no migrar. Al decidir la expresión regular apareció que limitarla a `{1,30}` reconocería el prefijo de 30 de una serie de 31 y contradiría el caso límite: eso corrigió la **especificación** (la definición de etiqueta, ver SDD 01 paso 7), no el plan. Se añadió `Locale.ROOT` por la misma revisión |
| Dividir (paso 4) | Se aplicó la tabla de "buena tarea" a las 7 tareas: T4 es la más grande (7 criterios) y se dejó junta con razón escrita; la validación se separó (T5); se añadió T6 porque protege una restricción (la clave). Ninguna tarea sin criterio; ningún criterio sin tarea. **No se conservaron borradores anteriores de las tareas**: la tabla ya lleva las correcciones aplicadas |

## Las pruebas, antes que el código (paso 5, historial)

Commit "Pruebas de aceptación…" antes que el commit "T1": 29 escenarios de Karate y `PruebaEtiquetas` (19 casos). Sobre la línea base: **4 de 29 escenarios pasan** (los que ya se cumplían: sin parámetro, 401 sin clave, 200 al consultar) y `PruebaEtiquetas` no compila. Progreso por tarea: T1 4/29 (no cambia el servidor), T2 19/29, T3 20/29, T4 23/29, T5 29/29.

## Paso 7: la cuenta

| Qué | Cuántas |
|---|---|
| Veces que el resultado se desvió de la especificación | 0: el código lo escribió quien escribió la especificación (con un asistente distinto, cuéntalas tú) |
| Veces que se añadió algo no pedido | 0 (se comprueba: sin `/api/etiquetas`, sin filtro múltiple) |
| Correcciones al plan | 1 (la expresión de 30 caracteres) |
| Veces que una prueba falló por error de la prueba, no del código | 3, todas al escribir los escenarios en Karate 2.1.4: en un `Scenario Outline` las celdas son texto salvo que la columna termine en `!` (`etiquetas!`); un parámetro vacío no se envía con `param`, hay que armar la URL; y `['viejo #' + marca]` en el lado derecho de un `match` no se evalúa como expresión |

## Paso 8: tabla final

`tabla-verificacion.md`: los 15 criterios pasan, cada uno por su etiqueta `@criterio-N`. Revisión manual de casos límite: `?etiqueta=%23%23a` 400, `?etiqueta=` 400, `#a` sin codificar 400, `etiqueta` repetida usa la primera.

## Paso 9: el experimento de control

`control/Servidor.java`: la implementación que sale de la frase "agrega etiquetas a las notas de Bitácora", sin especificación. **Está escrita a mano como un atajo típico; no es el resultado de otro asistente, así que muestra la mecánica del experimento, no la tasa real de desvíos de un asistente. Repite el experimento tú con tu asistente.** Hace lo que esa frase invita a hacer: guarda las etiquetas con la nota, permite `?tag=`, distingue mayúsculas, y añade un listado de etiquetas "por completitud".

| | Con especificación | Control |
|---|---|---|
| Pruebas de aceptación | 29/29 | **3/29** |
| ¿Cambia la forma de `GET /api/notas`? | No | **Sí**: `notas` pasa de lista de textos a lista de objetos (rompe a los clientes) |
| Fuera de alcance | nada | **añade `GET /api/etiquetas`** (listar todas las etiquetas) |
| Migración | ninguna | cambia el formato del archivo de notas (`texto|etiquetas`) |

## Paso 10: tiempos y errores baratos

No medí tiempo de persona. Lo que sí se puede decir es dónde se descubrió cada hallazgo y dónde habría sido más barato:

| Hallazgo | Fase donde apareció | Fase donde era más barato |
|---|---|---|
| Serie de 31+ caracteres: prefijo frente a serie completa | Plan (revisión de la expresión) | Especificación (la contradicción ya estaba en el texto de la sesión 01) |
| `#` sin codificar llega vacío | Aclarar | Especificación |
| `etiqueta` repetida | Aclarar | Aclarar (ya fue ahí) |
| Columnas tipadas en Karate, parámetro vacío | Pruebas | Pruebas (es de la herramienta, no de la especificación) |
