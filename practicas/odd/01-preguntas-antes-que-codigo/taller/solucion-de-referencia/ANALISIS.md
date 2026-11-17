# Taller ODD 01: preguntas antes que código

Versiones: JDK 21 (OpenJDK de Homebrew), Bitácora tal como está en `docker-kubernetes/app/api/Servidor.java`, nginx 1.27-alpine, jq del sistema. `comprobar.sh` repite todo y comprueba cada criterio.
`PREGUNTAS.md` se escribió antes que `Servidor.java`. Esta carpeta no guarda el historial que pedía el taller; en tu rama, ese orden lo prueba `git log`.

## Paso 2: qué responde la salida original

La salida original es una línea de texto por petición: `<hora> <MÉTODO> <URI> <estado> <N>ms`.

| # | Pregunta | ¿Se podía? | Por qué |
|---|---|---|---|
| 1 | Proporción de 5xx | Sí, con `awk` sobre el estado | |
| 2 | Rechazos por motivo | No | Un 400 no dice si fue nota vacía o demasiado larga; un 401 sí, pero un 404 de ruta y un 404 de otra cosa no se distinguen |
| 3 | p95 por ruta | Parcial | La URI trae la consulta (`?ms=300`), que parte las rutas en muchas |
| 4 | Notas por hora | Parcial | Se cuentan los 201 de `POST`, pero un `POST` que falla al escribir se confunde |
| 5 | Longitud de las notas | No | No se registra |
| 6 | Notas inválidas | No | Ver 2 |
| 7 | ¿Por qué tardó esta petición? | No | Sin identificador no se sigue una petición, y sin la longitud no se ve la causa |
| 8 | ¿Por qué un 503? | No | El 503 de `/salud/listo` y el de `/api/*` son iguales |
| 9 | ¿Qué tienen en común las lentas? | No | Falta `nota_longitud` |
| 10 | Seguir una petición de nginx a la API | No | No hay identificador compartido |

## Paso 3: el evento por petición

Una línea JSON por petición, `evento: "peticion"`:

| Campo | Tipo | Sirve a |
|---|---|---|
| `ts` | texto ISO-8601 | 4 (por hora), cronología de todo |
| `evento` | texto | distinguir los tipos de evento |
| `servicio`, `version`, `host` | texto | 8, 9 (¿una sola copia? ¿desde el despliegue?) |
| `correlacion` | texto | 7, 10 |
| `metodo`, `ruta` | texto | 3 (la ruta sin consulta, para no partir las series) |
| `estado` | entero | 1, 2, 8 |
| `resultado` | `exito` / `rechazo_cliente` / `error_servidor` | 1, 2 |
| `motivo` | texto, opcional | 2, 6, 8 (`clave_invalida`, `nota_invalida`, `metodo_no_permitido`, `ruta_desconocida`, `no_listo`, `excepcion`) |
| `duracion_ms` | entero | 3, 7, 9 |
| `nota_longitud` | entero, opcional | 5, 6, 9 |
| `carga_ms` | entero, opcional | 3, 9 (distingue una `/api/carga` pedida lenta de una lenta por culpa del servidor) |

Un campo que no sirve a ninguna pregunta se quitó: no hay `agente`, `ip`, ni la consulta completa.

## Paso 6: el 503 por no estar listo

Cae en `error_servidor`, porque el estado manda. Pero no es un fallo: el servicio se declaró no listo. Por eso `motivo=no_listo`: quien calcula la tasa de errores del usuario puede excluirlo (o contarlo aparte), y quien investiga un fallo real no lo confunde. Comprobado con una copia que arranca con 3 s de retraso: `/api/info` y `/salud/listo` dan 503 con ese motivo.

## Paso 7: qué no se registra

- **Contenido de las notas**: no. Es dato del usuario, puede traer secretos personales, y para el diagnóstico basta su longitud. `comprobar.sh` guarda una nota con un texto único y comprueba que no aparece en la salida.
- **Claves** (`X-Clave`): nunca, ni la buena ni la equivocada. Una clave equivocada suele ser una clave buena con un error de dedo.
- **Identificador de correlación recibido**: solo si cumple `[A-Za-z0-9._-]{1,64}`. Si no, se descarta y se genera otro: un cliente podría mandar saltos de línea o un texto enorme para falsificar o inflar el registro.

## Pasos 8 a 10: salida de `comprobar.sh`

Las consultas, con `jq`, están en `comprobar.sh` (P1 a P9). Con 12 `POST` (3 de 450 caracteres con el defecto activo), 8 `GET` y 4 rechazos:

- P1: 0 de 24 peticiones fueron 5xx. P2: un rechazo de cada motivo (clave, método, nota inválida, ruta). P3: `POST /api/notas` p95 = 2005 ms; todo lo demás, 15 ms o menos salvo `/api/carga?ms=300`.
- P4 y P5: 10 notas en la hora; longitud media 144, máxima 450. P6: 1 nota inválida.
- P7 y P9: la petición más lenta es un `POST /api/notas` de 2005 ms con `nota_longitud=450`, y **las únicas tres peticiones de más de un segundo son esas tres, todas con `nota_longitud=450`**. Con solo la salida, el defecto del paso 10 se encuentra: lo común a las lentas es el campo `nota_longitud`, que sin el paso 3 no existiría.
- P8: no hubo 503 en el tráfico; se comprobó aparte (paso 6).
- P10: con `X-Correlacion` se encuentra la misma petición en la línea de nginx y en la de la API (comprobado: el identificador que nginx devuelve es el que la API registró).

Qué falta (preguntas que aún no se responden bien): "por qué es lenta" se responde por correlación, no por causa: el evento dice *qué* tienen en común, no *dónde* se gasta el tiempo dentro de la petición. Eso lo dan las trazas (sesión 02).

## Paso 9: `nota.guardada`

Es un evento de negocio, no de transporte: pasa una vez por nota *realmente guardada* (una nota que dio 201 pero falló al escribir no lo emite) y trae `longitud` y `notas_total`. Responde lo que el de petición no puede: cuántas notas hay en el sistema en cada momento, y cuánto crece por hora aunque cambien las rutas o se añada otro modo de guardar. `comprobar.sh` comprueba 10 eventos para 10 notas, con `notas_total` creciente.

## Paso 10: el defecto

`DEFECTO_NOTA_LARGA_MS=2000` hace que las notas de más de 400 caracteres tarden 2 s. Quien reciba solo la salida JSONL encuentra la causa con la consulta de P9, sin código: ver arriba. Si el evento no trajera `nota_longitud`, las lentas parecerían aleatorias.

## Cambios a la aplicación (en la copia, no en el repositorio)

`Servidor.java` solo difiere del original en: eventos JSON en lugar de líneas de texto, `X-Correlacion`, `motivo`, `nota.guardada` y el defecto opcional. `default.conf.template` es la plantilla de nginx con el identificador (`$request_id`) y su registro de acceso en JSON.
