# Bitácora: la aplicación de práctica

Todos los talleres de [Docker](../docker/) y [Kubernetes](../kubernetes/) usan esta aplicación. Es pequeña a propósito: aquí se aprende a empaquetar, conectar y operar, no a programar la aplicación.

| Parte | Qué es | Carpeta |
|---|---|---|
| API | Un servidor HTTP en un solo archivo Java, sin dependencias | [`api/`](api/) |
| Web | Una página estática y una plantilla de nginx que reenvía `/api/` a la API | [`web/`](web/) |

Si vienes de la ruta de Angular o de React, cuando termines los talleres repite los de imágenes con tu propia aplicación en lugar de `web/`.

## Ejecutarla sin contenedores

Necesitas un JDK 21 o posterior.

```bash
cd api
java Servidor.java
```

En otra terminal:

```bash
curl localhost:8080/api/info
curl -X POST --data 'primera nota' localhost:8080/api/notas
curl localhost:8080/api/notas
```

## Rutas de la API

| Ruta | Qué hace |
|---|---|
| `GET /salud/vivo` | Responde 200 mientras el proceso funcione |
| `GET /salud/listo` | 200 si puede atender; 503 mientras arranca, si no puede escribir datos o si se está apagando |
| `GET /api/info` | Mensaje, versión, nombre del host que respondió y visitas de esa copia |
| `GET /api/notas` | Lista de notas guardadas |
| `POST /api/notas` | Guarda el cuerpo de la petición como una nota |
| `GET /api/carga?ms=300` | Gasta CPU durante ese tiempo (máximo 5000 ms) |

## Configuración

Todo llega por variables de entorno. Ninguna es obligatoria.

| Variable | Por defecto | Para qué |
|---|---|---|
| `PUERTO` | `8080` | Puerto en el que escucha |
| `MENSAJE` | `Hola desde la bitácora` | Texto que devuelve `/api/info` |
| `VERSION` | `dev` | Etiqueta de versión que devuelve `/api/info` |
| `DIRECTORIO_DATOS` | `datos` | Carpeta donde se guarda `notas.txt` |
| `CLAVE_API` | vacía | Si tiene valor, `POST /api/notas` exige la cabecera `X-Clave` con ese valor |
| `RETARDO_ARRANQUE_MS` | `0` | Simula un arranque lento: tarda ese tiempo en estar lista |
| `ESPERA_APAGADO_MS` | `0` | Tiempo que espera, ya sin estar lista, antes de cerrar al recibir la señal de terminación |

La web solo necesita `DESTINO_API`, la dirección de la API (por ejemplo `http://api:8080`).

## Tres comportamientos que los talleres aprovechan

1. **Las notas se guardan en un archivo.** Si el contenedor se recrea sin un volumen, se pierden. Con varias copias, cada una tiene su propio archivo.
2. **`host` dice quién respondió.** Así se ve el balanceo entre copias.
3. **Viva no es lo mismo que lista.** Si no puede escribir en `DIRECTORIO_DATOS`, el proceso sigue vivo pero `/salud/listo` devuelve 503 y la razón queda en el registro.

## Estado del código

La API se compiló y se ejecutó con JDK 21 al preparar el curso, y se probaron todas sus rutas. La página y la plantilla de nginx se ejecutaron con `nginx:1.28-alpine` en los talleres de Docker 02 a 05: la página carga, la plantilla se procesa con la variable `DESTINO_API` y `/api/` llega a la API.
