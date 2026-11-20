# 05 — Docker Compose

## Idea

En la sesión anterior montaste dos contenedores con seis órdenes largas, en un orden concreto. Eso es dictarle a un albañil cada ladrillo. Compose es entregarle el plano: describes qué servicios quieres, cómo se conectan y qué guardan, y una sola orden lo levanta todo.

## Lo esencial

Un archivo `compose.yaml` declara:

| Sección | Qué describe |
|---|---|
| `services` | Los contenedores: imagen o construcción, puertos, entorno, dependencias |
| `volumes` | Los volúmenes con nombre |
| `networks` | Las redes. Si no declaras ninguna, Compose crea una y conecta todos los servicios |
| `secrets` / `configs` | Archivos sensibles o de configuración que se montan en los servicios |

Cada servicio se resuelve en la red por su nombre. No hace falta crear la red ni publicar puertos entre ellos.

### Arrancar en orden no es estar listo

`depends_on` solo, espera a que el otro contenedor **arranque**. Una base de datos tarda unos segundos en aceptar conexiones después de arrancar. Para esperar de verdad:

```yaml
depends_on:
  db:
    condition: service_healthy
```

y el servicio `db` necesita un `healthcheck`. Aun así, una aplicación seria reintenta la conexión: en producción la base también puede reiniciarse cuando la aplicación ya está arriba.

### Órdenes

| Orden | Para qué |
|---|---|
| `docker compose up -d --build` | Construir lo necesario y levantar todo |
| `docker compose ps` | Estado y salud de los servicios |
| `docker compose logs -f api` | Registros de un servicio |
| `docker compose exec db psql -U postgres` | Ejecutar algo dentro de un servicio |
| `docker compose down` | Detener y eliminar contenedores y red |
| `docker compose down -v` | Lo mismo, y además borra los volúmenes |
| `docker compose config` | Mostrar el archivo final, con variables resueltas. Útil para validar |

## Ejemplo

Una aplicación cualquiera con su base de datos.

### Antes

<!-- file: ejemplo/antes/compose.yaml -->
```yaml
services:
  app:
    image: mi-app:latest
    ports:
      - "8080:8080"
    environment:
      DB_HOST: localhost
      DB_PASSWORD: SuperSecreta123
    depends_on:
      - db
  db:
    image: postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_PASSWORD: SuperSecreta123
```

### Análisis

| Problema | Consecuencia |
|---|---|
| `latest` y `postgres` sin versión | Levantar el proyecto dentro de seis meses puede traer otra base de datos |
| `DB_HOST: localhost` | La aplicación se busca a sí misma |
| Contraseña en el archivo | Queda en el repositorio para siempre |
| `depends_on` sin condición | La aplicación arranca antes de que la base acepte conexiones y falla de forma intermitente |
| Sin volumen | `down` y `up` borran la base |
| Puerto de la base publicado | Expuesta sin necesidad |

### Después

<!-- file: ejemplo/despues/compose.yaml -->
```yaml
services:
  app:
    image: mi-app:1.4.2
    ports:
      - "8080:8080"
    environment:
      DB_HOST: db
      DB_PASSWORD_FILE: /run/secrets/clave_db
    secrets:
      - clave_db
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped

  db:
    image: postgres:17
    environment:
      POSTGRES_PASSWORD_FILE: /run/secrets/clave_db
    secrets:
      - clave_db
    volumes:
      - datos-db:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -h 127.0.0.1 -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  datos-db:

secrets:
  clave_db:
    file: ./clave_db.txt
```

<!-- file: ejemplo/despues/.gitignore -->
```
clave_db.txt
```

- Versiones fijas.
- La contraseña vive en un archivo que no se versiona y se monta en `/run/secrets/`. La imagen de PostgreSQL acepta `POSTGRES_PASSWORD_FILE`; la aplicación tiene que saber leer la suya de un archivo.
- La aplicación espera a que la base esté sana.
- La comprobación usa `-h 127.0.0.1` a propósito: durante la primera inicialización la imagen de PostgreSQL arranca un servidor temporal que solo escucha en el socket Unix. Sin `-h`, `pg_isready` daría «sano» en ese momento y la aplicación arrancaría antes de que la base acepte conexiones por red.
- Los datos están en un volumen con nombre.
- La base no publica ningún puerto.

## Errores comunes

- Usar Compose de desarrollo, con montajes de carpetas y contraseñas de juguete, como si fuera producción.
- Confiar en `depends_on` sin comprobación de salud.
- `docker compose down -v` por costumbre, y adiós a los datos.
- Un archivo `.env` con secretos subido al repositorio.
- Nombres de contenedor fijos (`container_name`) que impiden levantar dos copias del proyecto.
- Publicar los puertos de todos los servicios.

## Taller

**Objetivo:** describir Bitácora completa en un archivo y levantarla con una orden.

Archivo de inicio: [`taller/inicio/compose.yaml`](taller/inicio/compose.yaml). Levanta, pero está lejos de ser correcto.

<!-- file: taller/inicio/compose.yaml -->
```yaml
# Punto de partida del taller. Funciona a medias: tu trabajo es dejarlo bien.
services:
  api:
    image: bitacora-api:1.0.0
    ports:
      - "8080:8080"

  web:
    image: bitacora-web:0.1.0
    ports:
      - "8081:8080"
```

1. Copia el archivo a `taller/solucion/` junto con la carpeta `app/` y tus Dockerfile. Haz que Compose **construya** las imágenes en vez de depender de que existan.
2. Configura la web para que encuentre a la API. La página debe funcionar completa.
3. Deja de publicar la API: solo la web debe ser alcanzable desde tu equipo.
4. Las notas deben sobrevivir a `docker compose down` seguido de `docker compose up`.
5. Añade a la API una comprobación de salud y haz que la web espere a que la API esté sana. Pruébalo con `RETARDO_ARRANQUE_MS=15000`: mientras la API arranca, la web no debe estar levantada.
6. Protege la escritura con `CLAVE_API` sin que el valor aparezca en `compose.yaml` ni en el repositorio. Explica qué opción elegiste y qué limitaciones tiene.
7. Saca a un archivo `.env` lo que cambia entre personas (puerto publicado, mensaje, versión) y versiona un `.env.ejemplo`.
8. Añade un servicio opcional con un **perfil**: PostgreSQL 17 con volumen y comprobación de salud, que solo se levante con `--profile datos`. La API no lo usa; el punto es practicar perfiles y salud.
9. Levanta dos copias independientes del proyecto a la vez (pista: `-p`). Si chocan, averigua qué lo impide y corrígelo.
10. Añade límites de memoria y CPU a la API.
11. Valida el archivo con `docker compose config` y pega la salida en el análisis.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Un clon limpio levanta con una orden | `docker compose up -d --build` tras copiar `.env.ejemplo` a `.env` |
| La API no es alcanzable desde el equipo | `curl localhost:8080` falla |
| Las notas persisten | `down`, `up`, y la nota sigue |
| Ningún secreto en el repositorio | Revisión del historial de la rama |
| Todos los servicios llegan a `healthy` | `docker compose ps` |
| Dos copias conviven | Dos proyectos levantados a la vez |

**Entrega:** rama `taller/docker-05`, con `compose.yaml`, `.env.ejemplo`, `.gitignore` y `taller/solucion/ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Compose es la herramienta del entorno local y de las pruebas de integración: quien llega nuevo al equipo clona, ejecuta una orden y tiene la aplicación con su base y su broker. Para producción con varias máquinas se usa un orquestador; ese es el salto a Kubernetes.

## Preguntas senior

1. ¿Qué resuelve Compose que no resuelva un guion con órdenes `docker run`?
2. ¿Por qué `depends_on` no garantiza que la dependencia esté lista, y por qué la aplicación debe reintentar aunque uses `service_healthy`?
3. ¿Dónde pondrías los secretos en desarrollo y dónde en producción?
4. ¿Qué diferencia hay entre `down` y `down -v`?
5. ¿Usarías Compose en producción? ¿En qué caso sí?
6. ¿Cómo logras que el entorno local se parezca a producción sin copiar su complejidad?
