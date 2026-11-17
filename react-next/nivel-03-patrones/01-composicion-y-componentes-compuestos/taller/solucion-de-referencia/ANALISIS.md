# Taller nivel-03/01: composición. Comparaciones y mediciones

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-03-patrones/01-composicion-y-componentes-compuestos/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.tsx` (Vitest, jsdom) y `e2e/n3-01.spec.ts` (Chromium, `next start`). Código: `compuestos.tsx`, `demo.tsx`.

## Punto 1. Pestañas con teclado

Flechas izquierda y derecha, Inicio y Fin mueven el foco y activan la pestaña (roving tabindex); la prueba recorre las cuatro teclas y comprueba `aria-selected` y el foco. El uso del README (`UsoPestanas`) funciona con el componente del README.

## Punto 4. Acordeón compuesto frente a acordeón de arreglo

Mismo comportamiento, dos APIs (ambos en `compuestos.tsx`):

| | Compuesto: `<Acordeon><Acordeon.Item><Acordeon.Encabezado>…` | De arreglo: `<AcordeonDeArreglo secciones={[…]} />` |
|---|---|---|
| Líneas de implementación | 44 (líneas 58 a 101) | 15 (líneas 102 a 116) |
| Uso para dos secciones | 4 líneas de JSX | 1 línea con datos |
| Contenido libre en el encabezado | **sí**: la prueba pone `<small>(con ícono 💳 y lo que quieras)</small>` dentro del encabezado de "Pago" | no sin ampliar el tipo (`titulo: string` hoy; habría que pasar `ReactNode` o añadir `renderTitulo`) |
| Una sección condicional, un hueco extra, otro componente en medio | se escribe donde se necesita | exige una propiedad nueva por cada caso |
| Servir datos que ya vienen como arreglo (una API) | hay que mapear a `Item` | directo |
| Mantener un orden o una estructura obligatoria | nada la impone: se puede olvidar el `Panel` | el tipo la impone |

Decisión: el de arreglo gana en simplicidad cuando las secciones son datos homogéneos (preguntas frecuentes de una API); el compuesto gana en flexibilidad cuando cada sección tiene estructura propia. La prueba también muestra la diferencia de comportamiento declarada: el compuesto abre uno a la vez (`aria-expanded` del anterior pasa a `false`); el de arreglo con `multiple` deja varias abiertas.

## Punto 5. Qué JavaScript llega con un componente de cliente que recibe `children` de servidor

`/n3/01-composicion` renderiza `PanelDesplegable` (cliente, abre y cierra) con `HistorialDePedidos` (servidor, `server-only`) como `children`. Medido en Chromium contra la compilación de producción (`mediciones/n3-01.json`):

| Pregunta | Resultado |
|---|---|
| ¿El resultado del servidor (los pedidos H-1 y H-2) está en el HTML? | **sí**, dentro del panel cerrado |
| ¿Aparece en algún `.js` la constante `historial-solo-servidor-7c1f` definida solo en `historial.tsx`? | **no** (10 archivos de JavaScript, 501 625 bytes revisados) |
| ¿Llega el código del panel de cliente (`aria-expanded`)? | **sí**, lo necesita para abrir y cerrar |

Es decir: el componente de cliente no arrastra a sus `children` al paquete. Se pone `'use client'` en la caja y el contenido de servidor pasa como dato ya renderizado; el código de la consulta se queda en el servidor.
