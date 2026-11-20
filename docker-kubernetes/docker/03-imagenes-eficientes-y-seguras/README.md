# 03 — Imágenes eficientes y seguras

## Idea

Un restaurante tiene cocina y comedor. En la cocina hay cuchillos, fogones y cajas de verduras; al comedor solo sale el plato terminado. Nadie le sirve al cliente la tabla de picar. Una imagen bien hecha separa igual: se construye en un sitio con todas las herramientas y se entrega solo el resultado.

## Lo esencial

### Construcción en varias etapas

Un Dockerfile puede tener varios `FROM`. Cada uno abre una etapa, y `COPY --from=` trae archivos de una etapa anterior. La imagen final es solo la última etapa: el compilador, el código fuente y las dependencias de desarrollo se quedan atrás.

### Orden para aprovechar la caché

De lo que menos cambia a lo que más cambia:

1. Imagen base.
2. Descriptor de dependencias (`pom.xml`, `package.json` y su archivo de bloqueo).
3. Instalación de dependencias.
4. Código fuente.
5. Compilación.

Así, cambiar una línea de código no vuelve a descargar las dependencias.

### Imagen base

| Opción | A favor | En contra |
|---|---|---|
| Completa (Debian, Ubuntu) | Compatibilidad, herramientas para diagnosticar | Más peso y más paquetes que parchear |
| Reducida (`-slim`) | Buen equilibrio | Faltan algunas utilidades |
| Alpine | Muy pequeña | Usa otra biblioteca de C (musl); a veces hay diferencias de comportamiento o rendimiento |
| Sin distribución (*distroless*) | Casi nada que atacar | No hay intérprete de órdenes para entrar a mirar |

No hay una respuesta única. La decisión es cuánto estás dispuesto a perder en diagnóstico a cambio de superficie de ataque.

### Usuario sin privilegios

Por defecto, el proceso del contenedor es `root`. Si alguien explota un fallo de tu aplicación, hereda ese usuario. Crea uno propio, con un identificador numérico fijo, y cambia a él con `USER`. Un puerto por debajo del 1024 exige privilegios: escucha en uno alto.

### Señales y comprobación de salud

- El proceso debe ser PID 1 y atender `SIGTERM` (forma exec en `ENTRYPOINT`).
- `HEALTHCHECK` le dice a Docker cómo saber si el contenedor está sano. Kubernetes lo ignora y usa sus propias sondas, pero en Docker y Compose sí cuenta.

## Ejemplo

Una aplicación de frontend (Angular, React o cualquiera que se compile con Node) servida con nginx.

### Antes

<!-- file: ejemplo/antes/Dockerfile -->
```dockerfile
FROM node:24
WORKDIR /app
COPY . .
RUN npm install
RUN npm run build
EXPOSE 3000
CMD npm run start
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Una sola etapa | La imagen lleva Node, el código fuente y todas las dependencias de desarrollo: más de un gigabyte para servir archivos estáticos |
| `COPY . .` antes de instalar | Cualquier cambio de código reinstala todas las dependencias |
| `npm install` | Puede resolver versiones distintas de las del archivo de bloqueo |
| Servir con el servidor de desarrollo | No está pensado para producción |
| `CMD` en forma shell | La señal de parada no llega al proceso |
| Proceso como `root` | Más daño si la aplicación se ve comprometida |

### Después

<!-- file: ejemplo/despues/Dockerfile -->
```dockerfile
# Etapa de construcción: aquí están Node y las dependencias de desarrollo
FROM node:24-alpine AS construccion
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Etapa final: solo nginx y los archivos generados
FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY --from=construccion /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
```

<!-- file: ejemplo/despues/.dockerignore -->
```
node_modules
dist
.git
.env*
*.md
```

- Las dependencias se instalan en una capa que solo se invalida si cambian `package.json` o el archivo de bloqueo.
- `npm ci` instala exactamente lo que dice el archivo de bloqueo.
- La imagen final no tiene Node. La variante `nginx-unprivileged` ya se ejecuta sin privilegios y escucha en el 8080.
- El `HEALTHCHECK` usa `127.0.0.1` y no `localhost`: dentro de esta imagen `localhost` se resuelve primero a la dirección IPv6 `::1`, y nginx solo escucha en IPv4. Con `localhost` el contenedor queda `unhealthy` aunque sirva bien.
- La carpeta de salida depende de la herramienta: `dist/` en Vite, `dist/<proyecto>/browser` en Angular. Ajústala.

Los dos Dockerfile (antes y después) se construyeron con un proyecto real de Vite 8 y Docker 29.9.3: la imagen del «antes» pesa unos 1,7 GB y la del «después» unos 81 MB; esta última sirve la página, corre como usuario 101, no contiene Node y llega a `healthy`.

## Errores comunes

- Copiar el código antes de instalar las dependencias.
- Dejar el compilador en la imagen final "por si acaso".
- Crear el usuario pero olvidar darle permiso sobre las carpetas donde la aplicación escribe.
- Elegir Alpine sin probar la aplicación en ella.
- Un `HEALTHCHECK` que depende de una herramienta que la imagen final no trae.
- Pasar un secreto con `ARG` o `ENV`: queda guardado en el historial de la imagen.

## Taller

**Objetivo:** llevar la imagen de la API de Bitácora de "funciona" a "se puede desplegar".

Parte de tu Dockerfile de la sesión 02. No hay archivo de inicio.

1. Anota el punto de partida: tamaño, usuario con el que corre el proceso (`docker exec <c> id`) y tiempo de reconstrucción tras cambiar una línea de código.
2. Conviértelo a dos etapas: compila con el JDK y ejecuta con una imagen que solo tenga el entorno de ejecución.
3. Haz que el proceso corra con un usuario sin privilegios, con identificador numérico fijo. La aplicación necesita escribir en `DIRECTORIO_DATOS`: resuélvelo sin volver a `root`.
4. Añade un `HEALTHCHECK` que use `/salud/listo`. Antes de escribirlo, comprueba qué herramientas trae tu imagen final.
5. Arranca con `RETARDO_ARRANQUE_MS=15000` y observa con `docker ps` cómo cambia el estado de salud. Ajusta el `HEALTHCHECK` para que no marque el contenedor como enfermo mientras arranca.
6. Arranca con `--read-only`. ¿Qué falla? Hazlo funcionar manteniendo el sistema de archivos de solo lectura.
7. Limita la memoria a 64 MB con `--memory`. ¿Arranca? Ajusta las opciones de la JVM para que use el límite del contenedor y no la memoria del anfitrión.
8. Prueba una imagen base más pequeña que la que elegiste. Compara tamaño y comprueba que todo lo anterior sigue funcionando. Decide con cuál te quedas y por qué.
9. Etiqueta el resultado como `bitacora-api:1.0.0`.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| La imagen final no contiene el compilador | `docker run --rm --entrypoint sh bitacora-api:1.0.0 -c 'command -v javac'` no devuelve nada, o la imagen ni siquiera tiene `sh` |
| El proceso no es `root` | `docker exec <c> id` muestra un identificador distinto de 0 |
| Funciona con `--read-only` | `POST /api/notas` responde 201 |
| Se detiene en menos de cuatro segundos | `time docker stop <c>` |
| Llega a `healthy` con arranque lento | `docker ps` |
| Pesa menos que la versión de la sesión 02 | `docker images` |

**Entrega:** rama `taller/docker-03`, con el Dockerfile y `taller/solucion/ANALISIS.md`: una tabla antes y después, y la justificación de la imagen base.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

En Spring Boot, además del Dockerfile, existen `./mvnw spring-boot:build-image` (Buildpacks) y Jib, que construyen la imagen sin que escribas la receta. Son buenas opciones cuando el equipo no quiere mantener Dockerfile; conviene conocer el Dockerfile igual, porque es lo que vas a leer cuando algo no cuadre.

## Preguntas senior

1. ¿Qué problema resuelve la construcción en varias etapas además del tamaño?
2. Te piden reducir a la mitad el tiempo de construcción en la integración continua. ¿Qué miras primero?
3. ¿Por qué un identificador numérico de usuario y no solo un nombre?
4. ¿Qué pierdes y qué ganas con una imagen sin distribución?
5. ¿Cómo pasas un secreto que solo se necesita al construir (por ejemplo, una credencial de un repositorio privado) sin que quede en la imagen?
6. ¿Cuándo no vale la pena optimizar una imagen?
