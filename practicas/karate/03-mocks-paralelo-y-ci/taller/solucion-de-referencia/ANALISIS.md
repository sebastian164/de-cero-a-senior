# Taller Karate 03: paralelo, simulador, contrato, integración continua y carga

Versiones: Karate 2.1.4 (`karate-junit6`), JUnit 6.1.3, JDK 21, Maven 3.10.1, act 0.2.97 (imagen `catthehacker/ubuntu:act-24.04` más Maven 3.8) y actionlint para el flujo de CI; karate-gatling 2.1.4 con Gatling 3.16.0 para la medición de carga con la integración oficial.
Todo lo que sigue lo imprime `./comprobar.sh`; las cifras son de una ejecución en un portátil de 18 núcleos y cambian de una ejecución a otra.

## Pasos 1 a 3: paralelo

| Hilos | Suite entera (36 escenarios) | Sin `@lento` (30 escenarios) | Solo `@lento` (6 escenarios) |
|---|---|---|---|
| 1 | 6019 ms | 360 ms | 5874 ms |
| 2 | 5388 ms | 297 ms | |
| 4 | 5181 ms | 207 ms | |
| 8 | 5185 ms | 185 ms | 5165 ms |

**¿Dónde deja de mejorar y por qué?** Con la suite entera, en dos o cuatro hilos: de 6,0 s a 5,2 s y ahí se queda. La causa no es el número de hilos sino
**un escenario**: `/api/carga?ms=999999` (recortado a 5000 ms) dura cinco segundos él solo, y un escenario no se parte entre hilos.
Con ocho hilos la suite nunca puede tardar menos que su escenario más largo. Se comprueba quitando `@lento`: los otros 30 escenarios bajan de 360 ms a 185 ms
(casi el doble de rápido con 8 hilos; la ganancia se frena también por el arranque de cada hilo y porque todos hablan con la misma API de un solo proceso).
Solo `@lento` casi no mejora (5874 ms con 1 hilo y 5165 ms con 8): son seis escenarios y el mayor ocupa los 5 s.
Conclusión para el equipo: el paralelo ayuda mientras haya trabajo repartible; después manda el escenario más largo. Si ese escenario molesta, se mueve a su propio
trabajo de CI (es lo que hace la etiqueta `@lento`) en lugar de comprar más hilos.

**Paso 2 (datos compartidos).** Sí hubo un escenario que dependía de datos ajenos, y se encontró al revisar la suite contra una API recién arrancada, no en las
corridas de ejemplo: "una ruta que empieza como una conocida responde como la conocida" (`rutas.feature`) hace `GET /api/notas/abc` y comparaba con `esquemas.notas`, cuyo
`notas: '#[] #string'` **rechaza la lista vacía** en Karate 2.1.4. Solo pasaba si otro escenario (o una corrida anterior) ya había guardado una nota; contra una API vacía
fallaba con `match failed for name: 'notas'`, y en paralelo, con la API vacía, fallaba al azar (1 de 6 corridas con 4 hilos). Las "20 de 20" iniciales se habían medido contra
una API que ya tenía notas, así que no lo veían. Arreglo: `esquemas.notas` comprueba la lista con `'#? Array.isArray(_) && _.every(x => typeof x == "string")'`, que acepta `[]`
y sigue rechazando cualquier elemento que no sea texto; la longitud de cada nota se comprueba con `match each` donde antes se guarda una nota (`notas.feature`).
El resto de los escenarios guarda una nota con texto único (`Date.now()` y `Math.random()`) y comprueba que **está**, nunca cuántas hay; ninguno depende de los contadores
`visitas` más que para decir que subieron al menos uno.
**Paso 3.** La suite pasó 5 de 5 veces seguidas con 4 hilos contra una API con datos, y 10 de 10 veces con 4 hilos reiniciando la API (lista vacía) antes de cada corrida;
además cada `.feature` ejecutado solo contra una API vacía pasa. `comprobar.sh` repite las dos comprobaciones, que son las que habrían detectado el defecto.

## Paso 4 y 6: el simulador y el contrato

`Simulador.java` (`src/test/java`) cubre las cinco rutas y se controla con `PUT /control/modo` (`normal`, `503`, `lento`, `roto`): 503 en 1 ms, cinco segundos exactos (5,003 s) y un
cuerpo que declara `application/json` pero no lo es. La carpeta `contrato/` ejecuta las mismas comprobaciones de forma (`esquemas.js`) contra la API real y contra el simulador: 7 de 7 escenarios en ambos.

## Paso 5: la web de nginx contra el simulador, y los defectos de la página

Con `pagina.mjs` se ejecuta el script real de `docker-kubernetes/app/web/index.html` (con un DOM mínimo) contra el nginx del taller, apuntando al simulador:

| Modo | A los 0,5 s | A los 7 s | Envío de una nota |
|---|---|---|---|
| normal | mensaje, versión y host del simulador | igual | "La API respondió 401." |
| 503 | "No se pudo contactar a la API..." con `version="undefined"` y `host="undefined"` | igual | "La API respondió 503." |
| lento (5 s) | "Cargando…" sin nada más | mensaje y datos | "La API respondió 401." |
| roto | "No se pudo contactar a la API..." | igual | **"Nota guardada."** |

Defectos de la página (pasos, esperado, obtenido):

1. **Muestra `undefined`.** Pasos: simulador en modo 503 y abrir la página. Esperado: ningún dato, o un guion. Obtenido: `version="undefined"` y `host="undefined"` en pantalla junto al mensaje de error.
2. **Dice "Nota guardada." con un cuerpo que no es JSON.** Pasos: modo roto, escribir una nota y enviar. Esperado: un error (la respuesta no es válida, no se puede saber si se guardó). Obtenido: "Nota guardada.".
3. **No envía clave.** Pasos: contra la API con clave (o contra el simulador, que la exige igual), escribir una nota y enviar. Esperado: o un campo para la clave o un mensaje claro. Obtenido: "La API respondió 401." sin explicar qué falta.
4. **Con la API lenta (5 s) no hay indicador de espera ni tiempo máximo.** Pasos: modo lento y abrir la página. Esperado: un aviso de que tarda o un corte con un mensaje. Obtenido: "Cargando…" cinco segundos y nada más; con una API que no responde nunca, para siempre.
5. **No distingue 503 de roto.** Ambos muestran el mismo mensaje "No se pudo contactar a la API"; en el 503 la API sí contestó.

## Paso 7: un cambio incompatible

Se renombra `visitas` a `visits` en una copia de la API. El contrato contra la API cambiada **falla** (1 escenario) y avisa; actualizado el esquema, el contrato pasa contra la API cambiada y
**falla contra el simulador** (1 escenario), que quedó desactualizado: esa prueba es la que avisa de que el simulador se desvió. Sin la carpeta `contrato/` ninguna prueba avisaría.

## Pasos 8 y 9: integración continua

El flujo `.github/workflows/karate.yml` se ejecuta de verdad con **act** (no solo `actionlint`). La raíz del repositorio de CI es esta carpeta más `api/Servidor.java`
(la API que se prueba; en el repositorio del curso vive en `docker-kubernetes/app/api/`). `comprobar.sh` arma esa raíz en una carpeta temporal y corre:

| Ejecución | Resultado |
|---|---|
| `act push` | verde; 5 de 5 escenarios (solo humo); construye la imagen, la levanta con Compose, espera a `/salud/listo` |
| `act pull_request` | verde; 36 de 36 (suite completa); `informe-karate.zip` con `karate-summary.html` publicado |
| `act push` con la API defectuosa (201 cambiado por 200 al guardar) | **rojo**; 2 escenarios fallan; el paso "Publicar el informe" se ejecuta igual (`if: always()`); el informe nombra "una nota guardada aparece en la lista" y el motivo: `expected status: 201, actual: 200` |

Hallazgos al ejecutarlo de verdad: (a) el compilador por defecto de Maven 3.8 no entiende `maven.compiler.release`: se fijó `maven-compiler-plugin` 3.13.0 en el `pom.xml`;
(b) la imagen de act no trae Maven (la de GitHub sí): se usó `catthehacker/ubuntu:act-24.04` más `apt-get install maven`; (c) un flujo con rutas que no existían (versión anterior) habría fallado en el primer paso: ahora el flujo y la raíz son coherentes.
Lo que act no demuestra: los permisos y secretos reales de GitHub, el caché de Maven ni el tiempo de un ejecutor de GitHub.

## Paso 10: la prueba de carga, qué dice y qué no

Dos herramientas, las dos con `info.feature`:

- `CargaTest` (grupo de hilos propio, cada iteración es un `.feature` completo): 1 usuario, 20 iteraciones: p50 3 ms, p95 4 ms, 82 iteraciones/s; 8 usuarios: p50 3 ms, p95 6 ms, 628/s; 0 errores.
- **karate-gatling 2.1.4** (la integración oficial, que no se incluye en esta carpeta: se mide aparte con el mismo `info.feature`, con 8 usuarios concurrentes durante 10 s): unas 77 000 peticiones, 0 KO, p95 1 ms, unos 7 700 peticiones por segundo. Hallazgo al ejecutarlo: sin `.pooledConnections()` en el protocolo el cliente abre una conexión por petición y, a miles por segundo, agota los puertos efímeros del portátil (`SocketException: Resource temporarily unavailable`, del 2 % al 7 % de las peticiones en KO); con conexiones reutilizadas no hay ninguna.
  `CargaTest` se escribió primero y es lo que trae esta carpeta porque sirve sin añadir Gatling ni Scala al proyecto, a costa de medir también el costo del cliente Karate.

**Qué dice:** que en este portátil, con la API en un solo proceso Java y sin red, `/api/info` aguanta 8 usuarios sin errores y con milisegundos. Sirve para comparar antes y después de un cambio, en la misma máquina.
**Qué no dice:** nada de producción. El cliente y la API compiten por los mismos núcleos; no hay red real, balanceador, TLS ni base de datos; `/api/info` es la ruta más barata (no guarda en disco); el
portátil tiene otras cosas en marcha; las diferencias entre `CargaTest` (decenas de iteraciones por segundo) y Gatling (miles de peticiones por segundo) son una medida del costo del cliente, no de la API. No hay latencias de cola útiles con tan pocas muestras.

## Paso 11: la página para el equipo

### Qué hace Karate en Bitácora
**Qué se prueba con Karate.** El contrato HTTP de la API: rutas, métodos, códigos de estado, formas de respuesta (`esquemas.js`), reglas de negocio visibles por HTTP (límites de la nota, clave, recorte de `ms`), y que el simulador sigue pareciéndose a la API real (carpeta `contrato/`).
**Qué no.** La lógica interna (eso es de las pruebas unitarias del código, con TDD), cómo se ve la página (otra herramienta, de navegador), el rendimiento en producción, la seguridad más allá de la clave, ni un cambio de base de datos o de infraestructura.
**Cuándo corre cada parte.**
| Qué | Cuándo | Dónde |
|---|---|---|
| Humo (`@humo`, 5 escenarios, menos de 1 s en Karate) | en cada push | CI, contra la API recién construida |
| Suite completa (36 escenarios, 4 hilos) | en cada pull request | CI, igual |
| `@lento` (6 escenarios, 5 s) | en cada pull request (van en la suite completa); se pueden mover a un trabajo nocturno si molestan | CI |
| Contrato (7 escenarios) | en cada pull request que toque la API o el simulador | CI, contra la API real y el simulador |
| Carga (`CargaTest`, Gatling) | a mano, antes de una versión que cambie algo de rendimiento | portátil o un ejecutor fijo, nunca como criterio de un pull request |
**Quién arregla una prueba roja.** Quien abrió el cambio, antes de pedir revisión. Si el fallo está en el informe publicado (`informe-karate`) y es un cambio de comportamiento deseado, esa persona actualiza el escenario y el esquema en el mismo pull request; si es un defecto, se corrige la API, no la prueba.
Una prueba inestable (que falla una de cada veinte) no se reintenta ni se desactiva: se abre un incidente con su nombre y se arregla en la semana; hasta entonces se marca `@inestable` y se excluye del bloqueo con la prueba aún visible en el informe.
Si el simulador queda desactualizado (el contrato falla solo contra él), lo arregla quien cambió la API.
