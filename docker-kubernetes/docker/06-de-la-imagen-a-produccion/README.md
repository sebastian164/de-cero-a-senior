# 06 — De la imagen a producción

## Idea

Un medicamento no sale de la fábrica solo porque el químico diga que quedó bien. Lleva número de lote, fecha, composición impresa y pasa un control de calidad; si un lote sale malo, se sabe exactamente cuál retirar. Una imagen que va a producción necesita lo mismo: identidad, contenido conocido y revisión.

## Lo esencial

### Versionado y trazabilidad

| Etiqueta | Uso |
|---|---|
| `1.4.2` | Versión exacta. Es la que se despliega |
| `1.4`, `1` | Apuntan a la última de esa serie. Cómodas, pero se mueven |
| Resumen `sha256:…` | Inmutable. Es la referencia más segura |
| Identificador del commit | Une la imagen con el código que la produjo |
| `latest` | No dice nada. No se despliega |

Regla: **se construye una vez y esa misma imagen recorre todos los ambientes.** Lo que cambia entre desarrollo, pruebas y producción es la configuración, no la imagen. Si reconstruyes para producción, lo que probaste no es lo que desplegaste.

### Registro

Subir una imagen es `docker login`, `docker tag` y `docker push`. El nombre completo incluye el registro: `ghcr.io/usuario/bitacora-api:1.0.0`. Sin registro en el nombre, se asume Docker Hub.

### Arquitecturas

Un Mac con procesador Apple Silicon construye imágenes `arm64`. La mayoría de los servidores son `amd64`. Una imagen de una arquitectura no corre en la otra (o corre emulada, muy lento). `docker buildx build --platform linux/amd64,linux/arm64` construye para ambas y publica un solo nombre que sirve a cada máquina la variante correcta.

### Seguridad de la cadena de suministro

- **Análisis de vulnerabilidades:** herramientas como Trivy, Grype o Docker Scout comparan los paquetes de la imagen con bases de vulnerabilidades conocidas. La mayoría vienen de la imagen base: actualizarla es el arreglo más común.
- **Lista de componentes** (*SBOM*): el inventario de lo que contiene la imagen. Permite responder "¿estamos afectados?" el día que aparece una vulnerabilidad nueva.
- **Secretos al construir:** `RUN --mount=type=secret,id=…` deja el secreto disponible solo durante esa instrucción. `ARG` y `ENV` lo dejan en el historial.
- **Firma:** permite comprobar que la imagen la publicó quien dice.

### Límites y registros

- Sin límites, un contenedor puede consumir toda la memoria de la máquina y tumbar a los demás.
- La aplicación escribe sus registros en la salida estándar y el entorno los recoge. Dentro del contenedor no se rotan ni se guardan archivos de registro.

## Ejemplo

### Antes

```bash
docker build -t miusuario/app:latest .
docker push miusuario/app:latest
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Solo `latest` | Si producción falla, no hay forma de saber qué versión corría ni a cuál volver |
| Sin relación con el código | No se puede ir de la imagen al commit |
| Una sola arquitectura, la del equipo de quien construyó | En el servidor puede no arrancar (`exec format error`) |
| Construida en el portátil | Depende de lo que esa persona tuviera sin confirmar |
| Sin análisis | Nadie sabe qué vulnerabilidades lleva |

### Después

```bash
VERSION=1.4.2
COMMIT=$(git rev-parse --short HEAD)
IMAGEN=ghcr.io/miusuario/app

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --label org.opencontainers.image.revision="$COMMIT" \
  --label org.opencontainers.image.source="https://github.com/miusuario/app" \
  -t "$IMAGEN:$VERSION" \
  -t "$IMAGEN:$COMMIT" \
  --push .

trivy image --severity HIGH,CRITICAL --exit-code 1 "$IMAGEN:$VERSION"
```

- Dos etiquetas: la versión y el commit.
- Etiquetas de metadatos estándar para ir de la imagen al código.
- Dos arquitecturas bajo el mismo nombre.
- El análisis devuelve un código de error si hay vulnerabilidades altas o críticas, así que puede detener un flujo de integración continua.

Las órdenes se ejecutaron con Docker 29.9.3, Buildx 0.38.1 y Trivy 0.76.2, con un registro local (`registry:3`) en lugar de `ghcr.io` (que exige credenciales): `buildx` publicó las dos arquitecturas bajo un solo nombre, `imagetools inspect` las lista y la etiqueta `org.opencontainers.image.revision` lleva al commit. Las opciones de cada herramienta cambian con las versiones; confírmalas en su ayuda.

## Errores comunes

- Reconstruir la imagen para cada ambiente.
- Reutilizar una etiqueta de versión para una imagen distinta.
- Publicar desde el portátil.
- Ignorar el análisis porque "siempre sale algo".
- Tratar el análisis como una lista por cumplir, sin distinguir lo que de verdad es explotable en tu caso.
- Olvidar que las imágenes envejecen: una imagen sin vulnerabilidades hoy las tendrá en tres meses sin que nadie la toque.

## Taller integrador de Docker

**Objetivo:** publicar Bitácora como lo haría un equipo profesional y defender cada decisión.

1. Crea un repositorio propio con la carpeta `app/`, tus Dockerfile y tu `compose.yaml`.
2. Publica `bitacora-api` y `bitacora-web` en un registro (GitHub Container Registry o Docker Hub), con etiqueta de versión y etiqueta de commit.
3. Publica ambas para `amd64` y `arm64`. Demuestra con `docker buildx imagetools inspect` que el nombre contiene las dos.
4. Analiza las dos imágenes con una herramienta de vulnerabilidades. Clasifica los hallazgos altos y críticos: cuáles se arreglan actualizando la base, cuáles no tienen arreglo todavía y cuáles no te afectan, y por qué.
5. Corrige al menos un hallazgo y publica una versión nueva. No reutilices la etiqueta anterior.
6. Genera la lista de componentes de la API y guárdala en el repositorio.
7. Demuestra que la misma imagen sirve para dos ambientes: levanta dos copias con Compose, con distinto `MENSAJE` y `VERSION`, sin reconstruir.
8. Automatízalo: un flujo de integración continua (por ejemplo, GitHub Actions) que, al crear una etiqueta de versión en git, construya, analice y publique. El flujo debe fallar si aparece una vulnerabilidad crítica con arreglo disponible.
9. Si vienes de Angular o React: añade una tercera imagen con tu propia aplicación de frontend, en varias etapas, y sírvela en lugar de `web/`.
10. Escribe un `README.md` que le permita a alguien que no te conoce levantar el proyecto en cinco minutos.

**Criterios de aceptación**

- Las imágenes se pueden descargar y ejecutar desde otra máquina solo con el nombre y la versión.
- Ninguna usa `latest`, ni como base ni como etiqueta publicada.
- Desde una imagen se llega al commit que la produjo.
- El flujo automático publica sin intervención manual y sin secretos escritos en el repositorio.
- `ANALISIS.md` justifica imagen base, estrategia de etiquetas y tratamiento de vulnerabilidades.

**Entrega:** enlace a tu repositorio en un pull request de la rama `taller/docker-06` de este repositorio, con `taller/solucion/ANALISIS.md`.

Los registros y los minutos de integración continua pueden tener costo según tu plan. Revisa los límites gratuitos antes de empezar.

## En el trabajo real

En la mayoría de los equipos nadie publica imágenes a mano: lo hace el flujo de integración continua, que es el único con credenciales del registro. El análisis de vulnerabilidades corre en cada construcción y también de forma periódica sobre lo ya desplegado, porque las vulnerabilidades nuevas aparecen sobre imágenes viejas.

## Preguntas senior

1. ¿Por qué construir una sola vez y promover la misma imagen entre ambientes?
2. ¿Qué estrategia de etiquetas usarías y qué problema evita cada etiqueta?
3. El análisis reporta cuarenta vulnerabilidades. ¿Cómo decides qué hacer?
4. ¿Qué es una lista de componentes y para qué sirve el día que aparece una vulnerabilidad grave?
5. ¿Por qué una imagen construida en un Mac puede no arrancar en el servidor?
6. ¿Cómo mantienes actualizadas las imágenes base de veinte servicios?

Con esto terminas la parte de Docker. Lo que sigue es qué pasa cuando hay más de una máquina: [Kubernetes](../../kubernetes/).
