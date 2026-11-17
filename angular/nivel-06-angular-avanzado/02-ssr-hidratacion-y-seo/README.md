# 02 — Renderizado en servidor, hidratación y SEO

## Idea

Hay dos formas de entregar un mueble: armado, listo para usar, o en una caja con instrucciones para que el cliente lo arme. Una aplicación de una sola página entrega la caja: el navegador recibe una página casi vacía y construye todo con JavaScript. El renderizado en servidor entrega el mueble armado: el HTML llega con el contenido, y luego JavaScript le da vida.

## Lo esencial

### Estrategias

| Estrategia | Dónde y cuándo se genera el HTML | Conviene para |
|---|---|---|
| CSR (en el cliente) | En el navegador | Aplicaciones tras autenticación, tableros internos |
| SSR (en el servidor) | En el servidor, en cada petición | Contenido dinámico y público |
| SSG / prerenderizado | Al construir | Contenido que cambia poco: páginas informativas, blog, catálogo estable |
| Híbrido | Se elige por ruta | La mayoría de aplicaciones reales |

Angular permite elegir el modo por ruta en la configuración de rutas del servidor.

### Qué se gana

- **Contenido visible antes** (mejor LCP), sobre todo en dispositivos lentos.
- **SEO y vistas previas** al compartir enlaces: los rastreadores reciben HTML con contenido.

### Qué cuesta

- Un servidor de Node que mantener y escalar (o una plataforma que lo haga).
- El código debe poder ejecutarse sin navegador.
- Más complejidad en caché, autenticación y despliegue.

No lo actives por defecto: un tablero interno tras inicio de sesión no gana nada.

### Hidratación

Tras recibir el HTML, Angular "hidrata": reutiliza el DOM existente y le conecta los eventos, en lugar de destruirlo y volver a crearlo. La **hidratación incremental**, apoyada en `@defer`, hidrata partes de la página solo cuando hacen falta.

**Desajuste de hidratación:** si la **estructura** del HTML del servidor y la que generaría el cliente difieren, Angular lanza `NG0500` (solo en desarrollo; en producción no avisa) y la vista afectada queda inconsistente (Angular no recrea el DOM; comprobado con Angular 22.2). Causas típicas: HTML inválido que el navegador repara (una `<table>` sin `<tbody>`: "expected `<tr>` but found `<tbody>`"), manipulación directa del DOM y acceso a `window` al renderizar. Un desajuste solo de **texto** (fechas con la zona horaria del servidor, valores aleatorios) no da error: el navegador reescribe el texto sin avisar y el usuario ve el valor del servidor un instante; es un fallo silencioso.

### Código compatible con el servidor

En el servidor no existen `window`, `document`, `localStorage` ni el tamaño de la pantalla.

```ts
// Mal: falla en el servidor
const ancho = window.innerWidth;

// Bien: solo en el navegador, tras el renderizado
constructor() {
  afterNextRender(() => {
    this.ancho.set(window.innerWidth);
  });
}

// Bien: tras una abstracción inyectable (DIP)
private readonly almacen = inject(AlmacenLocal);   // en el servidor, una implementación en memoria
```

Las peticiones hechas en el servidor se transfieren al cliente para no repetirlas al hidratar (caché de transferencia de `HttpClient`).

### SEO, lo básico

- Título y descripción únicos por página (`Title`, `Meta`).
- URLs legibles y estables; redirecciones correctas.
- HTML semántico: un `h1`, encabezados en orden, enlaces reales (`<a href>`), no botones que navegan.
- Datos estructurados (JSON-LD) donde apliquen.
- Etiquetas para vistas previas en redes.
- Códigos de estado correctos desde el servidor (404 real, no una página 200 que dice "no encontrado").
- `sitemap.xml` y `robots.txt`.
- Rendimiento: las métricas web influyen en el posicionamiento.

## Errores comunes

- SSR para una aplicación privada.
- Acceso directo a APIs del navegador en constructores o plantillas.
- Desajustes por fechas, aleatorios o identificadores generados (no avisan: el texto se reescribe en silencio).
- Olvidar `security.allowedHosts` al activar SSR: el servidor responde 400 a todas las peticiones hasta declarar los hosts permitidos.
- Peticiones duplicadas: una en el servidor y otra en el cliente.
- Guardar estado por usuario en una variable de módulo, una caché propia o un *singleton* que no pasa por la inyección de Angular: vive tanto como el proceso y se comparte entre peticiones de usuarios distintos. Un servicio `providedIn: 'root'` no se comparte: Angular lo crea de nuevo en cada petición (comprobado con tres peticiones seguidas).
- Páginas de error con código 200.
- Tokens o datos privados serializados en el HTML.

## Taller

1. Agrega renderizado en servidor a una aplicación con una parte pública y otra privada.
2. Configura por ruta: prerenderizado para la página de inicio, servidor para el detalle de producto, cliente para el área privada.
3. Provoca un desajuste de hidratación con una fecha y corrígelo.
4. Encuentra y corrige cada uso directo de `window` o `localStorage`.
5. Comprueba con las herramientas del navegador que las peticiones no se repiten al hidratar.
6. Agrega título, descripción y datos estructurados por página; verifica el HTML con JavaScript desactivado.
7. Mide LCP con y sin renderizado en servidor.

Rama `taller/ng-n6-02-ssr`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Cuándo vale la pena el renderizado en servidor y cuándo no?
2. ¿Qué es la hidratación y qué causa un desajuste?
3. ¿Cómo escribes código que funcione en servidor y en navegador?
4. ¿Qué riesgo tiene el estado en servicios compartidos del servidor?
5. SSR frente a prerenderizado: criterios.
