# 04 — Accesibilidad e internacionalización

## Idea

Una rampa en la entrada de un edificio la usa quien va en silla de ruedas, quien empuja un coche de bebé y quien lleva una maleta. La accesibilidad no es un extra para pocos: es buen diseño que sirve a todos, y en muchos sectores (incluido el público) es una obligación legal. Un senior de frontend la trata como parte de "terminado", no como una fase posterior.

## Lo esencial: accesibilidad

### Cuatro principios (WCAG)

| Principio | Significa | Ejemplos |
|---|---|---|
| Perceptible | Se puede percibir por más de un sentido | Texto alternativo, contraste, subtítulos |
| Operable | Se puede usar sin ratón | Todo alcanzable por teclado, foco visible, sin trampas |
| Comprensible | Se entiende | Etiquetas claras, errores explicados, comportamiento predecible |
| Robusto | Funciona con tecnologías de asistencia | HTML válido y semántico |

### La regla más rentable: HTML semántico

Un `<button>` trae gratis foco, activación con teclado, rol y estado para el lector de pantalla. Un `<div (click)>` no trae nada y obliga a reimplementarlo todo, casi siempre mal.

| En lugar de | Usa |
|---|---|
| `<div (click)="guardar()">` | `<button type="button" (click)="guardar()">` |
| `<span (click)="ir()">` | `<a routerLink="...">` |
| `<div class="titulo">` | `<h2>` |
| Un `div` con una lista | `<ul>` / `<ol>` |
| Texto suelto junto a un campo | `<label for="...">` |

ARIA es el último recurso: **ningún ARIA es mejor que ARIA mal usado.**

### Lista mínima

- Navegación completa por teclado, en orden lógico y con foco siempre visible.
- Cada campo con etiqueta; errores asociados (`aria-describedby`) y anunciados.
- Imágenes con texto alternativo (vacío si son decorativas).
- Contraste suficiente; la información no depende solo del color.
- Un encabezado principal y jerarquía ordenada.
- Diálogos: el foco entra, queda atrapado dentro y vuelve al cerrar; se cierran con Escape.
- Cambios dinámicos anunciados (`aria-live`, `role="status"`, `role="alert"`).
- Tras navegar, gestionar el foco y el título de la página.
- Respeto a `prefers-reduced-motion`.
- Zonas táctiles suficientemente grandes; funciona con zoom al 200 %.

Para componentes complejos (menús, pestañas, selectores, diálogos), usa primitivas ya resueltas (Angular CDK y similares) en lugar de construirlos desde cero.

### Cómo se verifica

1. Herramientas automáticas (axe, Lighthouse): detectan una parte.
2. Recorrer la aplicación solo con teclado.
3. Usarla con un lector de pantalla.
4. Probar con zoom y con contraste alto.

Las pruebas que buscan elementos por rol (sesión de pruebas del Nivel 4) verifican accesibilidad de paso.

## Lo esencial: internacionalización

Preparar la aplicación para varios idiomas y regiones no es solo traducir textos.

| Aspecto | Qué considerar |
|---|---|
| Textos | Fuera del código; nunca concatenar fragmentos (el orden de las palabras cambia entre idiomas) |
| Plurales y género | Reglas por idioma (formato ICU) |
| Fechas, números, moneda | Tuberías `date`, `number`, `currency` con la configuración regional |
| Dirección del texto | Derecha a izquierda: propiedades lógicas de CSS (`margin-inline-start`) |
| Longitud | Los textos traducidos pueden ocupar bastante más |
| Zona horaria | Guardar en UTC, mostrar en la zona del usuario |

Angular ofrece internacionalización en tiempo de compilación (una versión de la aplicación por idioma, máximo rendimiento) y hay bibliotecas de traducción en ejecución (cambio de idioma sin recargar). Se elige según si el cambio de idioma en caliente es un requisito.

Para usar el atributo `i18n` hay que agregar el paquete de localización (`ng add @angular/localize`): sin él, una plantilla con `i18n` falla al ejecutarse con `ReferenceError: $localize is not defined` (comprobado).

```html
<!-- Mal: no se puede traducir bien -->
<p>{{ 'Tienes ' + cantidad() + ' pedido' + (cantidad() === 1 ? '' : 's') }}</p>

<!-- Bien -->
<p i18n>{cantidad(), plural, =0 {No tienes pedidos} =1 {Tienes un pedido} other {Tienes {{ cantidad() }} pedidos}}</p>
```

## Errores comunes

- `div` y `span` con eventos de clic.
- Quitar el contorno del foco sin reemplazarlo.
- Solo marcadores de posición (*placeholder*) como etiqueta de un campo.
- Íconos sin nombre accesible.
- Diálogos que no gestionan el foco.
- Dejar la accesibilidad "para después".
- Textos incrustados y concatenados en el código.
- Fechas y números formateados a mano.

## Taller

1. Recorre una aplicación tuya solo con teclado y lista todo lo que no se pudo hacer.
2. Pásale una herramienta automática y corrige los hallazgos.
3. Reemplaza todos los `div` con clic por elementos semánticos.
4. Construye un diálogo accesible (o usa el del CDK) y verifica foco, Escape y anuncio.
5. Haz accesible un formulario completo: etiquetas, errores, foco al primer error.
6. Internacionaliza una pantalla a dos idiomas, incluidos plurales, fechas y moneda.
7. Agrega comprobaciones automáticas de accesibilidad a la integración continua.

Rama `taller/ng-n6-04-accesibilidad`.

## Preguntas senior

1. ¿Por qué empezar por HTML semántico antes que por ARIA?
2. ¿Cómo verificas la accesibilidad de una funcionalidad antes de darla por terminada?
3. ¿Qué debe cumplir un diálogo accesible?
4. ¿Por qué no se concatenan textos traducibles?
5. ¿Cómo incorporas la accesibilidad al proceso del equipo y no solo al código?
