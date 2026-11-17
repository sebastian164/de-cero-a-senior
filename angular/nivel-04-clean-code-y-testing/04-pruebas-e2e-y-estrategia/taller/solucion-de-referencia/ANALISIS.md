# Taller n4-04 — Pruebas E2E y estrategia

Versiones: Playwright 1.65.x (`@playwright/test`), `@axe-core/playwright` 4.14, Chromium headless. La aplicación bajo prueba es la tienda del ejemplo (`proyectos/.../04-pruebas-e2e-y-estrategia/tienda.ts`), servida desde `dist/` por `servidor/servidor.mjs` con una API simulada (`servidor/apis/n4-04.mjs`).

| # | Solución | Se ejecuta con |
|---|---|---|
| 1 | Tres recorridos críticos en `taller.e2e.ts`: 1a comprar (catálogo → carrito → confirmar, y el servidor recibe un pedido), 1b carrito vacío no confirma, 1c dos productos suman | `npx playwright test talleres/nivel-04-clean-code-y-testing/04-pruebas-e2e-y-estrategia` |
| 2 | Red simulada con `page.route`: 2a catálogo de una sola línea (independiente del servidor), 2b un 500 en `POST /pedidos` → alerta y el carrito se conserva (el error HTTP se declara como esperado) | idem |
| 3 | Accesibilidad: `new AxeBuilder({ page }).withTags(['wcag2a','wcag2aa'])` sobre el carrito: cero violaciones | idem |
| 4 | Presupuesto: `presupuesto.mjs` baja a 50 kB el límite de error del paquete inicial y comprueba que `ng build` falla: "bundle initial exceeded maximum budget. Budget 50.00 kB was not met by 278.36 kB with a total of 328.36 kB". `angular.json` se restaura siempre; el límite real del proyecto es 500 kB (aviso) y 1 MB (error) | `node presupuesto.mjs` |
| 5 | `ESTRATEGIA.md` | |
| 6 | `medir-niveles.mjs`: estático 1,5 s, unitarias y de componentes 6,0 s, E2E de la tienda 3,7 s (Apple Silicon, Node 26) y propuesta de reparto en `ESTRATEGIA.md` | `node medir-niveles.mjs` |

Sin pausas fijas (`waitForTimeout`) en el taller: las esperas son `expect(...)` y `expect.poll`. Selectores por rol y texto. Ninguna prueba depende del orden: el servidor se reinicia antes de cada una (`/api/_reiniciar` en `e2e/base.ts`).

## Hallazgo sobre el README

La prueba E2E del README (`un cliente puede hacer un pedido`) pasa tal cual, con una sola diferencia: la dirección inicial (`/catalogo`) es `/n4-04/ejemplo/catalogo` porque la tienda cuelga de la sesión. El texto `Total: $ 120.000` coincide con el formato de `CurrencyPipe` en `es-CO` con `symbol-narrow` y `1.0-0`.
