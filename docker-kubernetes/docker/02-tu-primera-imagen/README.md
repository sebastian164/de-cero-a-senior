# 02 — Tu primera imagen

## Idea

Una receta de cocina escrita paso a paso. Si mañana cambias solo el último paso, no vuelves a comprar los ingredientes ni a picar la cebolla: retomas desde donde cambió. Un Dockerfile es esa receta, y Docker guarda el resultado de cada paso para no repetirlo.

## Lo esencial

- **Dockerfile:** archivo de texto con las instrucciones para construir una imagen.
- **Capa:** el resultado de una instrucción que modifica archivos. Una imagen es una pila de capas de solo lectura.
- **Caché de construcción:** si una instrucción y sus entradas no cambiaron, Docker reutiliza la capa. En cuanto una capa cambia, todas las siguientes se reconstruyen.
- **Contexto:** la carpeta que le envías al motor al construir. Todo lo que hay en ella puede terminar en la imagen.

| Instrucción | Qué hace |
|---|---|
| `FROM` | Imagen de la que se parte |
| `WORKDIR` | Carpeta de trabajo para las instrucciones siguientes |
| `COPY` | Copia archivos del contexto a la imagen |
| `RUN` | Ejecuta una orden al construir y guarda el resultado en una capa |
| `ENV` | Define una variable de entorno |
| `EXPOSE` | Documenta el puerto que usa la aplicación. No lo publica |
| `USER` | Usuario con el que se ejecuta lo que sigue |
| `ENTRYPOINT` / `CMD` | El proceso que arranca el contenedor / sus argumentos por defecto |

**Forma exec y forma shell.** `CMD ["nginx", "-g", "daemon off;"]` ejecuta el programa directamente y lo deja como PID 1. `CMD nginx -g "daemon off;"` lo envuelve en un `sh -c`, y entonces quien recibe la señal de parada es el intérprete, no tu programa. Usa siempre la forma exec.

**Etiquetas.** `nombre:etiqueta` es un apodo movible. Lo que identifica de verdad a una imagen es su resumen (`sha256:…`).

## Ejemplo

Un sitio estático servido con nginx.

### Antes

<!-- file: ejemplo/antes/Dockerfile -->
```dockerfile
FROM ubuntu:latest
RUN apt-get update
RUN apt-get install -y nginx
COPY . /var/www/html
CMD service nginx start && tail -f /dev/null
```

### Análisis

| Problema | Consecuencia |
|---|---|
| `ubuntu:latest` | La construcción de hoy y la de mañana pueden dar imágenes distintas |
| `apt-get update` en su propia capa | Esa capa queda en caché con un índice viejo y el `install` siguiente puede fallar o traer paquetes antiguos |
| Un sistema operativo entero para servir archivos | Cientos de megabytes y muchos paquetes que parchear |
| `COPY .` sin `.dockerignore` | Entran `.git`, credenciales locales y lo que haya en la carpeta |
| `service nginx start && tail -f` | El PID 1 es un intérprete y luego un `tail`. Si nginx muere, el contenedor sigue "sano"; al detenerlo, nginx nunca recibe la señal |

### Después

<!-- file: ejemplo/despues/Dockerfile -->
```dockerfile
FROM nginx:1.28-alpine
COPY sitio/ /usr/share/nginx/html/
EXPOSE 80
```

<!-- file: ejemplo/despues/.dockerignore -->
```
.git
*.md
Dockerfile
```

<!-- file: ejemplo/despues/sitio/index.html -->
```html
<!DOCTYPE html>
<html lang="es">
<head><meta charset="utf-8"><title>Sitio de ejemplo</title></head>
<body><h1>Servido desde un contenedor</h1></body>
</html>
```

Tres líneas. La imagen oficial ya trae nginx configurado, se ejecuta en primer plano como PID 1 y atiende la señal de parada. Se copia solo la carpeta del sitio.

```bash
cd ejemplo/despues
docker build -t sitio:1.0.0 .
docker run -d --name sitio -p 8080:80 sitio:1.0.0
docker history sitio:1.0.0      # las capas y cuánto pesa cada una
```

## Errores comunes

- Copiar todo el contexto en vez de lo necesario.
- Separar `apt-get update` e `install` en dos instrucciones.
- Poner primero lo que cambia mucho: cada cambio de código invalida todas las capas siguientes.
- Usar la forma shell en `CMD` o `ENTRYPOINT`.
- Creer que `EXPOSE` publica el puerto.
- Escribir en el Dockerfile un valor que cambia por ambiente.

## Taller

**Objetivo:** construir las dos imágenes de [Bitácora](../../app/) y entender qué contiene cada capa.

Copia la carpeta `app/` a `taller/solucion/` y trabaja ahí.

1. **Web.** Escribe un Dockerfile para `web/` basado en la imagen oficial de nginx. La página va a la carpeta que sirve nginx y `default.conf.template` a `/etc/nginx/templates/`. Averigua en la documentación de la imagen qué hace con esa carpeta. Construye `bitacora-web:0.1.0` y arráncala con `DESTINO_API` apuntando a una dirección IP cualquiera, por ejemplo `http://127.0.0.1:9`: la página debe cargar aunque la API aún no exista. Después arráncala con un nombre de host que no exista, mira los registros y explica la diferencia. Te la vas a volver a encontrar en Compose y en Kubernetes.
2. **API, versión sencilla.** Escribe un Dockerfile de una sola etapa para `api/` a partir de una imagen con JDK 21. Compila al construir, no al arrancar. Construye `bitacora-api:0.1.0`.
3. Arranca la API publicando su puerto y comprueba `/api/info` y `/salud/listo`.
4. Con `docker history` y `docker images`, anota el tamaño total y qué capa pesa más.
5. Cambia el valor por defecto de un mensaje en `Servidor.java` y reconstruye. ¿Qué capas se reutilizaron y cuáles no? Repite cambiando solo un comentario del Dockerfile al final del archivo.
6. Añade un archivo grande e inútil a la carpeta (por ejemplo, `dd if=/dev/zero of=basura.bin bs=1M count=200`). Construye, mira qué cambió en el tiempo y en el tamaño, y arréglalo sin borrar el archivo.
7. Detén la API con `docker stop` y mide cuánto tarda. Si tarda cerca de diez segundos, tu proceso no está recibiendo la señal: averigua por qué y corrígelo.
8. Guarda una nota con `POST /api/notas`, elimina el contenedor, créalo de nuevo y consulta las notas. Anota lo que ves; lo resolverás en la sesión 04.

**Criterios de aceptación**

- Ambas imágenes construyen desde cero con una sola orden.
- Ninguna imagen base usa `latest`.
- La API se detiene en menos de cuatro segundos.
- `basura.bin` no entra en ninguna imagen.
- `ANALISIS.md` explica la caché con tus propias mediciones.

**Entrega:** rama `taller/docker-02`, con los Dockerfile, los `.dockerignore` y `taller/solucion/ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

El Dockerfile vive en el repositorio de la aplicación y pasa por revisión como cualquier otro código. Una mala decisión aquí se paga en cada construcción de la integración continua: minutos de más, multiplicados por cada cambio de cada persona del equipo.

## Preguntas senior

1. ¿Qué es una capa y por qué el orden de las instrucciones afecta al tiempo de construcción?
2. Diferencia entre `ENTRYPOINT` y `CMD`, y entre sus formas exec y shell.
3. ¿Por qué `EXPOSE` no basta para llegar a la aplicación?
4. Borras un archivo secreto en un `RUN` posterior al `COPY` que lo trajo. ¿Sigue en la imagen?
5. ¿Qué es el contexto de construcción y qué riesgos tiene enviarlo completo?
6. ¿Por qué una etiqueta no garantiza que dos personas estén usando la misma imagen?
