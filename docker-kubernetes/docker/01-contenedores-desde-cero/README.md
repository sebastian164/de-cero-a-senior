# 01 — Contenedores desde cero

## Idea

Un apartamento amoblado en un edificio: tiene su puerta, sus muebles y sus llaves, pero comparte con los vecinos los cimientos, el agua y la luz. Una casa independiente tiene todo propio, y por eso cuesta más construirla y mantenerla. El contenedor es el apartamento; la máquina virtual es la casa.

## Lo esencial

- **Contenedor:** un proceso normal del sistema, pero aislado. El núcleo de Linux le da su propia vista de los procesos, la red y los archivos (*namespaces*) y le limita los recursos (*cgroups*). No trae su propio núcleo: usa el del anfitrión.
- **Imagen:** el paquete inmutable del que nace un contenedor. Trae los archivos de la aplicación y todo lo que necesita para ejecutarse.
- **Registro:** el almacén de imágenes. Docker Hub es el que se usa si no dices otro.
- **Motor:** el servicio que construye imágenes y arranca contenedores. La orden `docker` es solo su cliente.

| | Máquina virtual | Contenedor |
|---|---|---|
| Qué aísla | Un sistema operativo completo | Un proceso |
| Núcleo | Propio | El del anfitrión |
| Arranque | Decenas de segundos | Menos de un segundo |
| Tamaño típico | Gigabytes | Megabytes |
| Aislamiento | Más fuerte | Más débil: comparten núcleo |

En macOS y Windows no hay núcleo de Linux, así que Docker Desktop arranca por debajo una máquina virtual pequeña con Linux y los contenedores viven dentro. Por eso `localhost` y los archivos a veces se comportan distinto que en un servidor Linux.

### Ciclo de vida

```
imagen ──run──> en ejecución ──stop──> detenido ──rm──> eliminado
                     │                    │
                     └──────start─────────┘
```

Un contenedor detenido conserva sus archivos. Uno eliminado, no. La imagen no cambia nunca: todo lo que el contenedor escribe queda en una capa propia que desaparece con él.

### Órdenes del día a día

| Orden | Para qué |
|---|---|
| `docker run` | Crear y arrancar un contenedor desde una imagen |
| `docker ps` / `docker ps -a` | Ver los que están en ejecución / todos |
| `docker logs -f` | Leer lo que el proceso escribe en su salida |
| `docker exec -it <c> sh` | Abrir una terminal dentro de uno en ejecución |
| `docker stop` / `docker rm` | Detener / eliminar |
| `docker images` / `docker pull` | Ver las imágenes locales / descargar una |
| `docker inspect` | Ver toda la configuración de un contenedor o imagen |

## Ejemplo

```bash
# -d: en segundo plano. --name: nombre propio. -p anfitrión:contenedor publica el puerto.
docker run -d --name web -p 8080:80 nginx:1.28-alpine

curl -I localhost:8080          # responde nginx
docker logs web                 # la petición aparece en su salida
docker exec -it web sh          # una terminal dentro
#   ps aux    -> solo ve sus propios procesos; nginx es el PID 1
#   hostname  -> el identificador del contenedor
#   exit

docker stop web && docker rm web
```

### Lo que suele salir mal la primera vez

```bash
docker run nginx                # 1
docker run -p 80 nginx          # 2
docker run -d nginx:latest      # 3
```

1. Sin `-d` la terminal queda ocupada, y sin `-p` nadie puede llegar al puerto desde fuera.
2. Con un solo número, Docker publica el 80 del contenedor en un puerto al azar del anfitrión.
3. `latest` no es "la última versión": es una etiqueta que hoy apunta a una imagen y mañana a otra. Lo que funcionó ayer puede dejar de funcionar sin que hayas cambiado nada.

La versión cuidada es la del ejemplo: en segundo plano, con nombre, con el puerto explícito y con una versión fija.

## Errores comunes

- Creer que un contenedor es una máquina virtual ligera y entrar a "instalarle cosas". Lo que instales desaparece con él.
- Acumular contenedores detenidos y luego no entender por qué el nombre "ya está en uso".
- Buscar los registros en un archivo dentro del contenedor en vez de usar `docker logs`.
- Publicar un puerto que la aplicación no está escuchando.
- Usar `latest`.

## Taller

**Objetivo:** manejar el ciclo de vida de un contenedor y comprobar por ti mismo qué está aislado y qué no.

No hay archivos de inicio. Trabaja en la terminal y anota cada orden y su resultado.

1. Arranca nginx con una versión fija, en segundo plano, publicado en el puerto 8081. Demuestra que responde.
2. Arranca un segundo nginx de la misma imagen en el 8082. Entra al primero y cambia el contenido de `/usr/share/nginx/html/index.html`. ¿Cambia el segundo? Explica por qué.
3. Elimina el primero y vuelve a crearlo. ¿Sigue tu cambio? Explica por qué.
4. Arranca PostgreSQL 17 con una contraseña pasada por variable de entorno. Sin instalar nada en tu equipo, crea una tabla y consulta una fila usando `psql` dentro del contenedor.
5. Desde dentro del contenedor de PostgreSQL lista los procesos. Desde tu equipo, busca ese mismo proceso (en macOS y Windows, explica dónde está realmente).
6. Detén un contenedor con `docker stop` y otro con `docker kill`. Mide cuánto tarda cada uno y revisa el código de salida con `docker inspect`. ¿Qué señal envía cada orden?
7. Deja tu equipo limpio: ni contenedores ni redes ni volúmenes creados por el taller.

**Criterios de aceptación**

- Cada respuesta se apoya en una orden y su salida, no en una suposición.
- Ningún contenedor usa `latest`.
- El paso 7 se demuestra con la salida de `docker ps -a`.

**Entrega:** rama `taller/docker-01`, archivo `taller/solucion/ANALISIS.md` con las órdenes, las salidas relevantes y tus explicaciones.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Casi nunca arrancarás contenedores a mano en producción, pero estas órdenes son las que usas cuando algo falla a las tres de la tarde: ver los registros, entrar a mirar, inspeccionar con qué configuración arrancó. Quien las domina diagnostica en minutos lo que a otros les toma una tarde.

## Preguntas senior

1. ¿Qué comparte un contenedor con el anfitrión y qué implica eso para la seguridad?
2. ¿Por qué un contenedor arranca en milisegundos y una máquina virtual no?
3. ¿Qué pasa con los datos escritos dentro de un contenedor cuando se elimina, y por qué está diseñado así?
4. ¿Por qué importa cuál es el proceso con PID 1 dentro del contenedor?
5. ¿En qué casos elegirías una máquina virtual en lugar de un contenedor?
6. Tu compañero dice "en mi máquina funciona" y usa la misma imagen que tú. Nombra tres cosas que aún pueden ser distintas.
