# Taller Karate 02: aserciones, datos y reutilización

Versiones: Karate 2.1.4 (`karate-junit6`), JUnit 6.1.3, JDK 21, Maven 3.10.1, Docker 29.9.3 (Compose para el ambiente `contenedor`). El proyecto es esta carpeta; `./comprobar.sh` comprueba todo lo de abajo.
La suite tiene 53 escenarios (los 36 de la sesión 01 más 17 de `esquemas-estrictos.feature`, paso 10). Pasan los 53 en los dos ambientes.

## Pasos 1, 3 y 4: datos y reutilización

- Escenarios que solo se diferenciaban en datos: ahora son `Scenario Outline` (notas rechazadas, claves malas, métodos no permitidos, caracteres especiales, valores de `ms`).
- Los datos de las notas rechazadas viven en `datos/notas-invalidas.json` (paso 3), no en el `.feature`.
- "Guardar una nota" está en `comun/guardar-nota.feature` y lo llaman seis escenarios (cuatro de `notas.feature`, uno de `info.feature` y el de `@falla`), más de los tres que pide el paso 4.

## Paso 2: renombrar un campo en la API

`comprobar.sh` cambia `visitas` por `visits` en una copia de `Servidor.java` y ejecuta la suite:

- **Fallan 2 escenarios de 53** ("informa su versión y quién respondió" y "cada consulta suma al menos una visita"). Los dos usan `esquemas.info`; el segundo además lee `response.visitas` por su nombre.
- **Sitios a corregir: uno**, la línea de `visitas` en `esquemas.js`. Con eso vuelve a pasar la suite salvo **un** escenario, el que usa `response.visitas` directamente. Esa lectura por nombre es la única copia del campo fuera del esquema; corregirla es un segundo sitio, y el hallazgo es que conviene leerlo del esquema o de una constante.
  Sin esquema compartido habría que tocar cada escenario que comprueba la forma de `/api/info`.

## Paso 5: ni una clave en el repositorio

La clave llega por `-Dbitacora.clave=<clave>`. `comprobar.sh` busca, en los `.feature`, el JSON y las clases de la suite y en `compose.yaml`: ni la clave de esta ejecución ni la del curso (`secreta`) ni una dirección `http://...` aparecen.
Sin la clave, la suite no arranca y dice: `Falta la clave de la API: ejecuta con -Dbitacora.clave=<clave>` (lo comprueba `comprobar.sh`). El mensaje sale de `karate-config.js`, antes de enviar la primera petición.

## Paso 6: dos ambientes

`karate.env` elige entre `local` (Bitácora con `java`, puerto 48080) y `contenedor` (Compose, puerto 48081, otra clave). La misma suite, sin tocar ningún `.feature`: 53 de 53 en `local` y 53 de 53 en `contenedor`.
Con la clave de un ambiente en el otro la API rechaza (401): cada ambiente tiene la suya. Un ambiente desconocido falla con `Ambiente desconocido: desconocido...`.

## Paso 7: la suite de humo

`@humo` son cinco escenarios (la suite de humo dice `ESCENARIOS 5 PASAN 5`). **Tarda 247 ms dentro de Karate; con Maven y el arranque de la JVM, 1,7 s.** `@lento` son seis y tardan unos 5,9 s (domina el de `ms=999999`, recortado a 5000).

## Paso 8: `match each`

La regla "todas las notas tienen al menos tres caracteres" (`match each response.notas == '#? _.length >= 3'`, escenario `@falla`) guarda antes una nota de dos caracteres (`ab`), que la API acepta. El mensaje de Karate:
`match each failed at index 15 (LIST:STRING)` y `$[15] | evaluated to 'false'`: dice **en qué posición** de la lista y que la condición dio falso; para ver cuál es la nota se mira la respuesta en el registro HTTP del informe.
Una lista vacía tampoco cumple `'#[] #string'` ni `match each` en Karate 2.1.4 (se anotó en el README de la sesión). Por eso `esquemas.notas` usa `'#? Array.isArray(_) && _.every(x => typeof x == "string")'`: acepta `[]`, que es una respuesta válida de una API recién arrancada (con `'#[] #string'`, el escenario de la ruta por prefijo fallaba solo contra una API vacía; `comprobar.sh` lo comprueba antes de nada).

## Paso 9: caracteres especiales

Comillas dobles, barras invertidas, tildes y eñes, emojis, un salto de línea, un tabulador, un carácter de control (`\u0001`) y etiquetas HTML: ocho casos guiados por datos, cada uno comprueba `responseType == 'json'`, la forma exacta y que la nota vuelve en la lista.
**¿La API devuelve siempre JSON válido? En los ocho casos, sí.** Las respuestas son JSON bien formado y la nota vuelve igual, con dos matices que la API decide por su cuenta y la prueba fija: el salto de línea y el tabulador se reducen a un espacio (`uno\ndos` vuelve como `uno dos`), y el carácter de control se conserva y se escapa como `\u0001`.

## Paso 10: ¿algún esquema era permisivo?

`esquemas-estrictos.feature` prueba cada esquema "al revés": 16 respuestas rotas que **no** deben cumplirlo (cuerpo vacío, objeto vacío, un campo menos, un campo de más, `visitas` como texto o en cero, versión sin forma, notas negativas, host vacío, lista con un número, nota guardada vacía, `ms` fuera de rango, error sin texto, rutas distintas), más un escenario que comprueba lo contrario: la lista vacía **sí** cumple `esquemas.notas`. Son los 17.
Resultado: **uno era permisivo**. `mensaje: '#string'` aceptaba `''`; el escenario "mensaje vacío" falló mientras estuvo así, y se endureció a `'#regex .+'` (que la API real sigue cumpliendo). Los demás ya rechazaban lo roto.
Lo que ya estaba bien: los objetos son cerrados (`match ==` falla ante un campo de más), `visitas` pide `>= 1`, la versión pide forma `x.y.z` o `dev`, y la lista de notas pide cadenas.
