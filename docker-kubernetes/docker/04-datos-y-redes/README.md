# 04 — Datos y redes

## Idea

Un contenedor es una habitación de hotel: cuando te vas, la limpian y no queda nada tuyo. Lo que quieres conservar lo guardas en la caja fuerte del hotel, que no depende de la habitación. Esa caja fuerte es un volumen. Y para hablar con otra habitación no gritas por el pasillo: llamas por el teléfono interno marcando su número. Ese teléfono interno es la red de Docker.

## Lo esencial

### Dónde viven los datos

| Tipo | Qué es | Cuándo usarlo |
|---|---|---|
| Capa del contenedor | Lo que el proceso escribe sin más | Nada que quieras conservar |
| **Volumen** | Almacenamiento que administra Docker, independiente del contenedor | Datos de bases de datos y de aplicaciones |
| **Montaje de carpeta** (*bind mount*) | Una carpeta de tu equipo vista dentro del contenedor | Desarrollo: editar código y verlo dentro |
| `tmpfs` | Memoria, nunca toca disco | Temporales y datos sensibles de corta vida |

Un volumen sobrevive a `docker rm`. Solo se borra si lo pides (`docker volume rm` o `docker rm -v`).

### Cómo se hablan los contenedores

- En la red por defecto (`bridge`), los contenedores se alcanzan por dirección IP pero **no por nombre**.
- En una red creada por ti, Docker ofrece un servidor de nombres interno: cada contenedor se resuelve por su nombre.
- Publicar un puerto (`-p`) es para entrar desde fuera. Entre contenedores de la misma red no hace falta: se usa el puerto interno.
- Dentro de un contenedor, `localhost` es el propio contenedor, no tu equipo ni el vecino.

## Ejemplo

### Antes

```bash
docker run -d --name db -e POSTGRES_PASSWORD=local -p 5432:5432 postgres:17
docker run -d --name app -e DB_HOST=localhost -p 8080:8080 mi-app:1.0.0
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Sin volumen | Al recrear `db` se pierde la base de datos entera |
| `DB_HOST=localhost` | La aplicación se busca a sí misma. Funciona fuera de contenedores y falla dentro: el clásico "en mi máquina funciona" |
| Red por defecto | Aunque pusieras `DB_HOST=db`, el nombre no se resolvería |
| Puerto 5432 publicado | La base queda expuesta en tu equipo sin necesidad |

### Después

```bash
docker network create tienda
docker volume create datos-db

docker run -d --name db --network tienda \
  -e POSTGRES_PASSWORD=local \
  -v datos-db:/var/lib/postgresql/data \
  postgres:17

docker run -d --name app --network tienda \
  -e DB_HOST=db \
  -p 8080:8080 \
  mi-app:1.0.0
```

- La base guarda sus archivos en el volumen `datos-db`.
- Los dos contenedores están en la red `tienda`, y `app` encuentra a la base por el nombre `db`.
- Solo se publica el puerto que alguien de fuera necesita.

La ruta de datos depende de la imagen y de su versión: la de PostgreSQL cambió a partir de la versión 18. Lee siempre la documentación de la imagen que uses.

```bash
docker rm -f db                           # se elimina el contenedor…
docker volume ls                          # …y el volumen sigue ahí
docker run --rm --network tienda nginx:1.28-alpine nslookup db    # resolución por nombre
```

## Errores comunes

- Guardar datos en la capa del contenedor y descubrirlo al actualizar la imagen.
- Usar `localhost` para llegar a otro contenedor.
- Publicar todos los puertos "para probar" y dejarlos así.
- Montar una carpeta del equipo en producción: ata el contenedor a esa máquina.
- Montar una carpeta vacía encima de una que la imagen traía con contenido, y tapar ese contenido.
- Problemas de permisos: el volumen pertenece a `root` y el proceso corre con otro usuario.

## Taller

**Objetivo:** que las notas de Bitácora sobrevivan y que la web hable con la API por nombre, usando solo `docker run`. Sin Compose todavía: primero hay que sufrirlo a mano para entender qué automatiza.

Usa tus imágenes `bitacora-api:1.0.0` y `bitacora-web:0.1.0`.

1. Reproduce el problema: guarda una nota, recrea el contenedor de la API y comprueba que se perdió.
2. Resuélvelo con un volumen. Demuestra que la nota sobrevive a eliminar y recrear el contenedor.
3. Tu proceso corre sin privilegios. Si el volumen te dio un error de permisos, explica por qué y arréglalo en la imagen, no ejecutando como `root`.
4. Arranca la API y la web en la red por defecto e intenta que la web llegue a la API por nombre. Documenta el error.
5. Crea una red propia, conecta ambos y configura `DESTINO_API` para que la página funcione completa: debe mostrar el mensaje y guardar notas.
6. Publica solo lo necesario. La API no debe ser alcanzable directamente desde tu equipo; la web sí. Demuéstralo.
7. Arranca una segunda API con otro nombre y otro `MENSAJE`, con el mismo volumen. Guarda una nota en cada una y lee desde ambas. ¿Qué ves? ¿Qué riesgo tiene que dos procesos escriban en el mismo archivo?
8. Cambia el contenido de `index.html` sin reconstruir la imagen de la web, usando un montaje de carpeta. Explica por qué eso es cómodo en desarrollo y mala idea en producción.
9. Limpia todo: contenedores, red y volumen.

**Criterios de aceptación**

- Las notas sobreviven a `docker rm -f`.
- La web llega a la API por nombre, no por IP.
- `curl localhost:8080/api/info` contra la API falla desde tu equipo; a través de la web funciona.
- Ningún contenedor corre como `root`.
- `ANALISIS.md` incluye las órdenes exactas en orden, de modo que otra persona pueda repetirlas.

**Entrega:** rama `taller/docker-04`, con `taller/solucion/ANALISIS.md` y los cambios que hayas hecho al Dockerfile.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

En producción los datos casi nunca viven en un volumen local del servidor: van a una base de datos administrada o a almacenamiento en red, porque el contenedor puede aparecer mañana en otra máquina. Lo que aprendes aquí —separar el proceso de sus datos y encontrar servicios por nombre— es exactamente la idea que Kubernetes lleva a varias máquinas.

## Preguntas senior

1. Diferencia entre un volumen y un montaje de carpeta, y cuándo usar cada uno.
2. ¿Por qué `localhost` dentro de un contenedor no es tu equipo?
3. ¿Cómo encuentra un contenedor a otro por nombre?
4. ¿Qué diferencia hay entre publicar un puerto y que dos contenedores estén en la misma red?
5. Dos copias de una aplicación escriben en el mismo volumen. ¿Qué puede salir mal y cómo lo evitarías en el diseño?
6. ¿Cómo harías una copia de seguridad de un volumen?
