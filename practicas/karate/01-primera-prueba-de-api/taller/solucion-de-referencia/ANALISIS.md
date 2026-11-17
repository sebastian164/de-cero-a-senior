# Taller Karate 01: la API de Bitácora, completa

## Paso 1: versión

Karate **2.1.4** (`io.karatelabs:karate-junit6`), JUnit 6.1.3, JDK 21, Maven 3.10.1. El proyecto es esta carpeta.
La suite se ejecuta desde `SuiteTest`, que usa `io.karatelabs.core.Runner` y se controla con propiedades:
`-Dbitacora.clave`, `-Dbitacora.url`, `-Detiquetas`, `-Descenario` y `-Dhilos`.
`./comprobar.sh` arranca Bitácora en el puerto 48080 y comprueba todos los criterios.

## Paso 2: el ejemplo de la sesión

Los tres archivos del ejemplo pasan contra Bitácora sin cambios en los `.feature`.
Lo único que no compilaba tal cual era `BitacoraTest.java`: no traía el `import` y la serie 2 lo exige.
En Karate 2.1.4 es `import io.karatelabs.junit6.Karate;`. Ya está corregido en el README del curso.

## Paso 3: la lista de casos, antes de las pruebas

| Ruta | Caso normal | Casos de error y bordes |
|---|---|---|
| `GET /salud/vivo` | 200, texto `vivo`, `text/plain` | Sigue en 200 mientras arranca |
| `GET /salud/listo` | 200, texto `listo` | 503 `no listo` mientras arranca; otro método (DELETE) |
| `GET /api/info` | 200 con la forma `{mensaje, version, host, visitas, notas}` | Las visitas suben; las notas suben al guardar; 503 mientras arranca; POST |
| `GET /api/notas` | 200 con `{host, notas: [texto]}` | Que la nota guardada esté, sin suponer posición ni cantidad |
| `POST /api/notas` | 201 con `{guardada, host}` | Sin clave 401; clave incorrecta 401 y no se guarda; vacía 400; solo espacios 400; 500 caracteres 201; 501 caracteres 400; comillas, barra y tildes vuelven iguales; espacios y saltos de línea se reducen |
| `PUT`, `DELETE /api/notas` | — | 405 con `{error}` |
| `GET /api/carga` | 200 con `{host, ms, resultado}` y tarda al menos `ms` | Sin parámetro 200 ms; 999999 se recorta a 5000; negativo es 0; `abc` |
| Ruta desconocida | — | 404 con `{error: 'ruta desconocida', rutas: [5 rutas]}`; una ruta que empieza como una conocida |

Cada fila tiene sus escenarios en `src/test/java/bitacora/`. 27 escenarios en total, más uno de arranque (`@arranque`) y uno que falla a propósito (`@falla`), que solo se ejecutan cuando se piden.

## Paso 6: `/api/carga` con valores raros

| Petición | Qué debería pasar, en mi opinión | Qué pasa (y es lo que la prueba fija) |
|---|---|---|
| `ms=999999` | Recortar a 5000, como dice la documentación | Recorta a 5000 y tarda al menos 5 s (`@lento`) |
| `ms=abc` | 400 diciendo que `ms` no es un número | 200 con los 200 ms por defecto (`@defecto`) |
| `ms=-5` | 0 o 400 | 0: responde enseguida con `resultado: 0` |

## Pasos 8 y 9

La suite pasa dos veces seguidas sin reiniciar la API: cada escenario crea su nota con un texto único
(`Date.now()` y `Math.random()`) y comprueba que **está**, nunca cuántas hay. Un escenario se ejecuta solo con
`-Descenario="una nota guardada aparece en la lista"` y los de humo con `-Detiquetas=@humo`.

## Paso 10: arranque lento

Lanzada contra una API con `RETARDO_ARRANQUE_MS` y sin esperar, la suite falla: 19 de 27 escenarios reciben 503.
Pasan los 8 que no dependen de que esté lista: `/salud/vivo`, las rutas desconocidas y los cinco de `/api/carga`,
que tampoco comprueba si la API está lista. La solución está en `karate-config.js`: antes de la primera prueba,
`karate.callSingle` llama a `comun/esperar-lista.feature`, que reintenta `GET /salud/listo` con
`retry until responseStatus == 200` hasta 150 veces cada 200 ms. Con eso, la suite lanzada a los 2 s de un arranque
de 10 s espera y pasa. No hay ninguna espera fija.

## Paso 11: el informe HTML

`target/karate-reports/karate-summary.html` lista los archivos con sus escenarios. El de un escenario que falla
(`feature-html/bitacora.falla-a-proposito.html`) muestra el paso exacto (`match response.version == '9.9.9'`),
el motivo (`match failed: EQUALS ... not equal (STRING:STRING) 'dev' '9.9.9'`), la línea, y el registro HTTP de la
petición (`1 > GET http://localhost:48080/api/info`) y de la respuesta, con el cuerpo donde se ve `"version": "dev"`.
También aparece la llamada de `esperar-lista.feature`. Es suficiente para entender el fallo sin volver a ejecutar.

## Paso 12: defectos de la API, como los reportaría

Los escenarios marcados `@defecto` fijan el comportamiento de hoy: si alguien lo corrige, fallan y avisan de que hay que actualizarlos.

**1. Las rutas responden a cualquier ruta que empiece como ellas.**
Pasos: `GET /api/notas/abc` (o `/api/notasx`, o `/api/infoxyz`). Esperado: 404 con el cuerpo de ruta desconocida.
Obtenido: 200 con la lista de notas (o la información). Causa probable: `HttpServer.createContext` enruta por prefijo.

**2. Las rutas de salud y `/api/info` no comprueban el método.**
Pasos: `DELETE /salud/listo`, `POST /api/info`. Esperado: 405, como hace `/api/notas`. Obtenido: 200. Además,
`POST /api/info` suma una visita.

**3. `/api/carga` acepta un `ms` que no es número.**
Pasos: `GET /api/carga?ms=abc`. Esperado: 400. Obtenido: 200 y 200 ms de CPU, sin avisar del valor ignorado.

**4. `/api/carga` atiende aunque la API no esté lista.**
Pasos: arrancar con `RETARDO_ARRANQUE_MS=20000` y pedir `GET /api/carga` de inmediato. Esperado: 503, como `/api/info`
y `/api/notas`. Obtenido: 200. Lo destapó el paso 10.

## Criterios de aceptación

| Criterio | Cómo se comprobó |
|---|---|
| Todas las rutas tienen caso normal y de error | La tabla del paso 3 frente a los escenarios |
| La suite pasa dos veces seguidas | `comprobar.sh`, vueltas 1 y 2 |
| Cada escenario pasa solo | `comprobar.sh`, por nombre y por etiqueta |
| No hay direcciones ni claves en los `.feature` | `comprobar.sh` busca `http://`, `localhost` y la clave en todos los `.feature` |

La dirección por defecto es `http://localhost:48080` y no `8080` para no chocar con otros servicios que ya usan el 8080 en tu máquina; cámbiala con `-Dbitacora.url=...`.
