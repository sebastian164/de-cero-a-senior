# 03 — Accesibilidad e internacionalización en React y Next.js

Los principios, la lista mínima de accesibilidad y los criterios de internacionalización no dependen del framework: están en la [sesión de la ruta Angular](../../../angular/nivel-06-angular-avanzado/04-accesibilidad-e-internacionalizacion/). Estúdiala primero. Aquí, cómo se aplican en React y Next.js.

## Accesibilidad en React

### JSX es HTML: usa el elemento correcto

| En lugar de | Usa |
|---|---|
| `<div onClick={guardar}>` | `<button type="button" onClick={guardar}>` |
| `<span onClick={() => router.push('/x')}>` | `<Link href="/x">` |
| `<div className="titulo">` | `<h2>` |
| `<>` con filas sueltas | `<ul>`, `<table>` con su estructura |

Particularidades de JSX: `htmlFor` en lugar de `for`, `className` en lugar de `class`, y los atributos `aria-*` se escriben igual que en HTML.

### Herramientas propias de React

- **`useId`** para asociar etiquetas, errores y descripciones con identificadores estables entre servidor y cliente.
- **Referencias** para gestionar el foco: llevarlo al primer error, devolverlo al cerrar un diálogo.
- **Fragmentos** para no introducir `div` que rompan la semántica de listas y tablas.
- **Bibliotecas de primitivas accesibles sin estilo** (React Aria, Radix, Base UI) para menús, diálogos, pestañas y selectores: resuelven teclado, foco y roles. No los reimplementes.
- **Reglas de accesibilidad para JSX en el *linter*.**
- **Testing Library:** consultar por rol y por etiqueta verifica la accesibilidad en cada prueba.

```tsx
function Campo({ etiqueta, error, ...resto }: CampoProps) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id}>{etiqueta}</label>
      <input id={id} aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined} {...resto} />
      {error && <p id={`${id}-error`} role="alert">{error}</p>}
    </div>
  );
}
```

### Navegación en una aplicación de una sola página

Al cambiar de ruta sin recargar, un lector de pantalla puede no enterarse. Next.js anuncia los cambios de ruta; aun así, cada página debe tener un título único y un encabezado principal, y el foco debe quedar en un lugar razonable.

### Contenido que aparece después

- Los estados de carga con `Suspense` deben reservar espacio (esqueletos) para no mover el contenido.
- Los mensajes que aparecen tras una acción (guardado, error) se anuncian con `role="status"` o `role="alert"`.
- Los botones en estado pendiente conservan un nombre comprensible y no pierden el foco.

### Imágenes

`next/image` exige el atributo `alt`. Texto descriptivo si la imagen informa; `alt=""` si es decorativa.

## Internacionalización en Next.js

### Rutas por idioma

El idioma forma parte de la URL (`/es/productos`, `/en/products`): es indexable, se puede compartir y permite renderizar en el servidor en el idioma correcto.

```
app/
└── [idioma]/
    ├── layout.tsx        <html lang={idioma}>
    ├── page.tsx
    └── productos/page.tsx
```

El código que intercepta las peticiones detecta el idioma preferido (cabecera `Accept-Language` o una cookie) y redirige a la ruta correspondiente.

### Textos

- En diccionarios por idioma, cargados **en el servidor**: los textos no viajan todos al navegador.
- Los componentes de cliente reciben solo los textos que usan, o se apoyan en una biblioteca (por ejemplo, next-intl) que lo gestiona.
- Nunca concatenar fragmentos: usar mensajes con variables y reglas de plural (formato ICU).

### Formato

La API `Intl` del lenguaje cubre fechas, números, moneda, plurales y listas:

```ts
new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' }).format(120000);
new Intl.DateTimeFormat('es-CO', { dateStyle: 'long', timeZone: 'America/Bogota' }).format(fecha);
new Intl.PluralRules('es').select(cantidad);
```

**Cuidado con la hidratación:** formatear una fecha con la zona horaria del servidor y luego con la del navegador produce HTML distinto y un error de hidratación. Fija la zona horaria explícitamente, o formatea solo en un lado. Y no es solo la zona horaria: el texto exacto que producen `Intl` y `toLocaleString` depende del motor. Comprobado con Node 26, Chromium y WebKit: `timeStyle: 'short'` da `9:30 p. m.` en Node y Chromium pero `9:30 p.m.` en WebKit, y una moneda sin `maximumFractionDigits` da `$ 60.000,00` en WebKit frente a `$ 60.000` en los otros. Si el servidor y el navegador pueden diferir, fija también esas opciones (`hour12: false`, `maximumFractionDigits: 0`) o formatea solo en el cliente; si no, React informa el error de hidratación #418.

### SEO multilingüe

Enlaces alternativos por idioma (`hreflang`), metadatos traducidos y un `sitemap` que incluya todas las variantes.

## Errores comunes

- `div` y `span` clicables.
- Diálogos y menús hechos a mano sin gestión del foco.
- Etiquetas no asociadas a sus campos.
- Quitar el contorno de foco.
- Íconos interactivos sin nombre accesible.
- Idioma guardado solo en estado de cliente: sin indexación y con parpadeo al cargar.
- Todos los diccionarios enviados al navegador.
- Textos incrustados en los componentes.
- Fechas formateadas con zonas horarias distintas en servidor y cliente.
- El atributo `lang` del documento fijo en un solo idioma.

## Taller

1. Recorre una aplicación tuya solo con teclado y corrige cada bloqueo.
2. Ejecuta una herramienta automática de accesibilidad y resuelve los hallazgos.
3. Reemplaza un diálogo o un menú propio por una primitiva accesible, o por `<dialog>` nativo.
4. Internacionaliza una sección a dos idiomas con rutas por idioma, incluidos plurales, fechas y moneda.
5. Provoca un error de hidratación por zona horaria y corrígelo.
6. Agrega comprobaciones de accesibilidad a las pruebas y a la integración continua.

Rama `taller/rx-n6-03-accesibilidad`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Por qué preferir primitivas accesibles ya resueltas?
2. ¿Qué hay que cuidar en la navegación de una aplicación de una sola página?
3. ¿Por qué el idioma debe ir en la URL?
4. ¿Cómo evitas errores de hidratación al formatear fechas?
5. ¿Cómo incorporas la accesibilidad a la definición de terminado?
