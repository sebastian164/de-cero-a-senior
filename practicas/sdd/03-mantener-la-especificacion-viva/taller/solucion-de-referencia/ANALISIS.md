# Taller SDD 03: mantener la especificación viva

Todo se ejecuta con `./comprobar.sh`, que reconstruye en una carpeta temporal el repositorio de las etiquetas (estado final de la sesión 02, tomado de `../../../02-del-plan-a-las-tareas/taller/solucion-de-referencia`) y le aplica cada paso con commits reales. Salidas guardadas aquí: `especificacion-final.md`, `cambio-005.md`, `cambio-006.md`. Herramientas: `herramientas/deriva.sh` y `anotar.py`. Karate 2.1.4, JDK 21, git del sistema.

## Paso 1: enlazar

`anotar.py` añade bajo cada criterio `_Prueba: @criterio-N en etiquetas.feature_`. `deriva.sh` comprueba que los 15 criterios nombran una prueba **que existe**: si se quita la etiqueta de uno de los escenarios, avisa ("el criterio 14 no tiene prueba ni justificación"). Criterios sin prueba: 0, así que no hubo que escribir justificaciones de "no automatizable"; la forma de justificar uno queda soportada (`_Sin prueba automática (criterio N): <razón>_`).

## Paso 2: un cambio en orden (un solo PR)

Rama `cambio-005-maximo-cinco`, fusionada con `--no-ff`. Orden en el historial: documento de cambio, especificación (criterio 6 y marca `limite-etiquetas`), pruebas, código. Con las pruebas nuevas y el código viejo: **28 de 29 pasan** (falla la que exige rechazar 6 etiquetas), y `deriva.sh` ya avisa ("la especificación dice 5 y el código tiene MAXIMO_ETIQUETAS=10"). Con el código nuevo: 29/29 y sin deriva.

## Paso 3: un cambio al revés

Rama `atajo`: la consulta por etiqueta deja de normalizar mayúsculas, sin tocar la especificación. **Lo que avisó fue una prueba** (el escenario de `@criterio-8`), de forma automática: 28/29. `deriva.sh` **no** lo ve, porque solo compara números y la normalización de *guardar*, no la de consultar. Sirve de lección: una herramienta de deriva barata no sustituye a las pruebas de aceptación. Segundo atajo, `atajo-limite`: el límite sube a 6 solo en el código; `deriva.sh` lo detecta sin levantar el servidor y la suite también falla (28/29).

## Paso 4: una urgencia

Error reproducido: una nota con espacio duro (U+00A0, que insertan algunos teclados móviles) antes del `#` devolvía `etiquetas: []`. El arreglo (una línea en el patrón) deja la nota en `['trabajo']`. En ese punto el código y la especificación **ya difieren** (el texto dice "después de un espacio"). `deriva.sh` no lo ve. Para dar la urgencia por cerrada: (1) arreglo en el código, (2) una prueba que reproduzca el error (se añadió una fila a la tabla de reconocimiento, 30/30), (3) la especificación al día (Definiciones: "incluido el espacio duro"), (4) un documento de cambio retroactivo (`cambio-006.md`), (5) la revisión del arreglo por otra persona. Los pasos 1 a 4 se hicieron en la rama `urgencia` y se fusionaron; el 5 depende de otra persona: pídesela a alguien.

## Paso 5: detección de deriva

No hay asistente aparte: lo hace `deriva.sh`. Encuentra el largo máximo de etiqueta cambiado solo en el código (30 frente a 40) y el límite de etiquetas. **Diferencias falsas inventadas: 0** sobre `main` sin deriva. Lo que no encuentra: el atajo del paso 3 y el espacio duro del paso 4 (son comportamiento, no números). Para eso están las pruebas.

## Paso 6: poda

Poda real, ejecutada por `comprobar.sh`: se quita la sección "Historias de usuario", que repetía el "Propósito" (marcar una nota con etiquetas y ver solo las de una etiqueta). La especificación pasa de 87 a 82 líneas, siguen los 15 criterios, `deriva.sh` no encuentra criterios sin prueba ni deriva y cabe en dos pantallas (se tomó 100 líneas como límite). `especificacion-final.md` es la versión podada.

## Paso 7: cuándo no (cambios reales del proyecto de este taller)

| Cambio | ¿SDD? | Por qué |
|---|---|---|
| Añadir etiquetas (sesiones 01 y 02) | **Habría ayudado, y ayudó** | Cambia el comportamiento que ven otros clientes; apareció 1 contradicción real (31 caracteres) y 6 preguntas que la frase no respondía; la versión sin especificación rompió la forma de la respuesta |
| Corregir una errata en el texto de un mensaje de error | **Habría estorbado** | Sin efecto en criterios ni en clientes; un documento de cambio costaría más que el cambio |
| Pasar el máximo de etiquetas de 10 a 5 (cambio 005) | **Dudoso** | Es una línea de código, pero cambia lo que un cliente ve (un 400 donde antes había 201). Se hizo con el proceso completo para ejercitarlo; en el trabajo real bastaría con una nota de una línea en la especificación y la prueba, sin el documento aparte |

## Paso 8: el costo

No medí tiempo de persona; estimación con lo contado:

- Añadido por el proceso en las etiquetas: 6 documentos (especificación, preguntas, aclaraciones, plan, tareas, casos) y 29 escenarios escritos antes del código.
- Errores que evitó, comprobados: la contradicción de los 31 caracteres (antes de escribir código); el cambio de forma de `GET /api/notas` y el listado de etiquetas no pedido (el experimento de control los produce, la especificación los prohíbe); el cambio 005 se verificó en rojo antes de tocar el código; los dos atajos del paso 3 los vio la suite o `deriva.sh` sin que nadie los leyera.
- Dónde se paga solo: cuando el cambio toca comportamiento observable por otros (contrato de una API, formato de datos) o más de unos tres criterios. Por debajo (una constante, un mensaje, un refactor sin efecto externo), la prueba existente y una línea en el commit bastan.

## Paso 9

La guía de una página está en `GUIA.md`.
