# 04 — Estrategias de renderizado y despliegue

Los nombres de algunas funciones y archivos especiales han cambiado entre versiones de Next.js. Confirma la sintaxis en la documentación de tu versión.

## Idea

Cada página responde una pregunta distinta: ¿este contenido es igual para todos?, ¿cada cuánto cambia?, ¿qué tan rápido debe verse? No existe una estrategia mejor en general; hay una adecuada para cada página, y una misma aplicación las combina.

## Lo esencial

### Estrategias

| Estrategia | Cuándo se genera el HTML | Conviene para |
|---|---|---|
| Estática | Al construir | Páginas informativas, documentación, blog |
| Estática con regeneración | Al construir, y se actualiza en segundo plano por tiempo o bajo demanda | Catálogo, fichas de producto |
| Dinámica en servidor | En cada petición | Contenido por usuario, datos al instante |
| Transmitida (*streaming*) | Se envía por partes a medida que resuelven | Páginas con secciones lentas |
| Cáscara estática con huecos dinámicos | La parte común se sirve de inmediato; lo personalizado llega después | Tienda con carrito, tablero con cabecera fija |
| En el cliente | En el navegador | Partes muy interactivas, sin SEO |

### Criterios de decisión

| Pregunta | Si la respuesta es sí |
|---|---|
| ¿Es igual para todos los usuarios? | Estática |
| ¿Cambia, pero tolera minutos de retraso? | Regeneración por tiempo |
| ¿Sé exactamente cuándo cambia? | Regeneración bajo demanda |
| ¿Depende de quién la pide? | Dinámica, o cáscara estática con hueco dinámico |
| ¿Tiene una parte lenta? | `Suspense` y transmisión |
| ¿Necesita indexarse en buscadores? | Que no sea solo de cliente |

### Páginas con parámetros

Para rutas dinámicas (`/productos/[id]`) se puede indicar qué valores generar al construir (`generateStaticParams`); el resto se genera a la primera visita y queda guardado.

### Metadatos y SEO

Cada ruta define sus metadatos (objeto `metadata` o función `generateMetadata` para los dinámicos). Además: `sitemap` y `robots` generados, imágenes para vistas previas, datos estructurados, y códigos de estado correctos con `notFound()` y `redirect()`.

### Optimización incorporada

- **Imágenes** (`next/image`): tamaños adaptados, formatos modernos, carga diferida y dimensiones reservadas para evitar saltos.
- **Fuentes** (`next/font`): alojadas con la aplicación, sin salto de diseño.
- **Scripts de terceros** (`next/script`): con estrategia de carga.
- **Enlaces** (`next/link`): precarga de las rutas visibles.

### Código que se ejecuta antes de la ruta

Un archivo en la raíz (llamado `middleware` en unas versiones y `proxy` en otras más recientes) intercepta las peticiones: redirecciones, reescrituras, comprobación rápida de sesión, idioma. Debe ser ligero. **No es el lugar de la autorización:** la comprobación definitiva va junto a los datos (sesión de seguridad).

### Entornos de ejecución

| Entorno | Características |
|---|---|
| Node.js | API completa; acceso a base de datos y bibliotecas nativas |
| *Edge* | Arranque casi instantáneo, cercano al usuario; API limitada |

### Despliegue

| Opción | A favor | En contra |
|---|---|---|
| Plataforma gestionada (Vercel y similares) | Cero configuración; caché, imágenes y vistas previas integradas | Costo a escala; dependencia del proveedor |
| Servidor de Node propio o en contenedor (`output: 'standalone'`) | Control y costo predecible; encaja con Kubernetes | Hay que resolver caché compartida entre réplicas, imágenes y CDN |
| Exportación estática (`output: 'export'`) | Cualquier alojamiento estático | Sin funciones de servidor |

Con varias réplicas propias, la caché en disco de cada instancia diverge: hace falta un manejador de caché compartido. Es el detalle que más sorprende al salir de una plataforma gestionada.

### Configuración

- Variables con prefijo `NEXT_PUBLIC_` se **incrustan en el paquete del navegador al construir**: nunca secretos, y cambiar su valor exige reconstruir.
- Las demás solo existen en el servidor.
- Para una misma imagen en varios ambientes, la configuración pública debe leerse en ejecución desde el servidor y pasarse al cliente.

## Ejemplo: una tienda

| Página | Estrategia | Por qué |
|---|---|---|
| Inicio, quiénes somos | Estática | Igual para todos, cambia poco |
| Listado de categoría | Regeneración por tiempo corto | Tolera unos minutos; mucho tráfico |
| Ficha de producto | Estática con regeneración bajo demanda al cambiar precio o existencias | Se sabe cuándo cambia |
| Carrito e indicador en la cabecera | Hueco dinámico o cliente | Por usuario |
| Resultados de búsqueda | Dinámica, con transmisión | Depende de la consulta |
| Mi cuenta, mis pedidos | Dinámica | Por usuario; sin indexar |
| Pago | Dinámica | Exactitud |

## Errores comunes

- Toda la aplicación dinámica "por si acaso", o toda estática sirviendo datos viejos.
- Secretos con prefijo `NEXT_PUBLIC_`.
- Autorización solo en el código que intercepta peticiones.
- Lógica pesada o consultas a la base de datos en ese código.
- Imágenes con `<img>` sin dimensiones.
- Probar el rendimiento y la caché en modo de desarrollo.
- Varias réplicas propias con caché local.
- Páginas de "no encontrado" que responden 200.
- Desplegar sin conocer qué rutas quedaron estáticas y cuáles dinámicas (la salida de la construcción lo indica).

## Taller

1. Para una aplicación de al menos seis páginas, elabora la tabla de estrategias con su justificación.
2. Impleméntala y revisa en la salida de la construcción que cada ruta quedó como esperabas.
3. Agrega transmisión a una página con una sección lenta y compara el tiempo hasta el primer contenido.
4. Agrega metadatos dinámicos, `sitemap` y `robots`.
5. Empaqueta la aplicación en un contenedor con salida independiente y ejecútala con dos réplicas; describe qué ocurre con la caché.
6. Demuestra que una variable `NEXT_PUBLIC_` queda en el paquete del navegador.
7. Mide las métricas web con y sin `next/image` y `next/font`.

Rama `taller/rx-n5-04-renderizado`.

## Preguntas senior

1. ¿Cómo eliges la estrategia de renderizado de una página?
2. ¿Qué es la regeneración bajo demanda y cuándo conviene?
3. ¿Qué resuelve la transmisión con `Suspense`?
4. ¿Qué cambia al desplegar Next.js por tu cuenta frente a una plataforma gestionada?
5. ¿Qué diferencia hay entre variables públicas y de servidor?
