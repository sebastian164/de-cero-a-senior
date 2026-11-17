# Taller n6-04 — Accesibilidad e internacionalización

Pantallas: `/n6-04/taller/antes` (inaccesible a propósito) y `/n6-04/taller/despues`. Pruebas: `taller.e2e.ts` (6) con `@axe-core/playwright` 4.14 (etiquetas WCAG 2.0/2.1 A y AA).

| # | Solución | Resultado |
|---|---|---|
| 1 | Recorrido solo con teclado de `antes` | `div.boton`, `span.enlace` y `span.icono` **no reciben el foco** y Enter no los activa: no se puede guardar, ir al inicio ni cerrar. Desde el título de la pantalla, Tab solo alcanza el campo de correo |
| 2 | axe sobre `antes` / `despues` | `antes`: `image-alt` (crítico) y `color-contrast` (serio, 2 nodos); `despues`: **0 violaciones** |
| 3 | `div`/`span` con clic → `<button type="button">`, `<a routerLink>`, botón con `aria-label` e ícono `aria-hidden`; `div.titulo` → `<h2>` | |
| 4 | Diálogo con `Dialog` del CDK: `role="alertdialog"`, `aria-modal`, nombre y descripción, foco al primer elemento, foco atrapado (Tab y Mayús+Tab), Escape cierra, el foco vuelve al botón y el resultado se anuncia con `LiveAnnouncer` | prueba "punto 4" (y axe sobre el diálogo abierto: 0) |
| 5 | Formulario con `label for`, `aria-invalid`, `aria-describedby` hacia el error (o la ayuda), resumen `role="alert"` y foco al primer campo con error | prueba "punto 5" |
| 6 | Compilación por idioma: `ng build n6-04-i18n` (`localize: ['es-CO','en']`, `messages.xlf` extraído con `ng extract-i18n` y `messages.en.xlf` traducido) | es-CO: "Tienes 5 pedidos", "jueves, 24 de diciembre de 2026", "$ 1.234.568"; en: "You have 5 orders", "Thursday, December 24, 2026", "$1,234,568"; la frase con variables se traduce entera |
| 7 | `ci/accesibilidad.yml` (GitHub Actions: `npm ci`, `playwright install`, `npm run build` y las pruebas con axe) | El YAML se leyó con Ruby; las mismas pruebas se ejecutaron en local |

## Hallazgos

- **axe no detecta el campo con solo `placeholder`**: lo acepta como nombre accesible. El README lo lista como error común; solo lo atrapa la revisión manual o la regla de `label` en una revisión de código. Por eso el punto 2 pide también el recorrido con teclado y la lectura de la pantalla.
- Los plurales ICU funcionan sin traducciones (`i18n` + `LOCALE_ID` `es-CO`) y con ellas; la compilación por idioma genera una carpeta por configuración regional (`es-CO/`, `en/`) con su `<base href>`.
- El ejemplo del README (`{cantidad(), plural, =0 {...}}` con `i18n`) se ejecutó en prueba unitaria y en las dos compilaciones.
