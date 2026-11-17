# Taller nivel-05/01: análisis

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-05-arquitectura/01-componentes-de-servidor-y-de-cliente/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Versiones: Next.js 16.4.6 (Turbopack), React 19.3.1. Todo se ejecutó con `next build` y `next start`.

## 1. Convertir una página de cliente

`proyectos/react-next/n5-01-servidor-cliente/antes/pagina-producto.tsx` es el ejemplo "Antes" del README tal cual (con `marked` en el
cliente, como dice el README) y `…/productos/pagina.tsx` es el "Después". `e2e/n5-01.spec.ts` las abre en Chromium y suma el JavaScript
descargado (cifras exactas en `mediciones/n5-01-javascript.json`, que genera cada ejecución).

Lo que cambia, más allá de los bytes:

| | Antes | Después |
|---|---|---|
| HTML inicial | "Cargando…" sin producto | Ya trae nombre, descripción convertida y la reseña se transmite |
| Peticiones | 2 `fetch` tras ejecutar JavaScript, a 2 endpoints creados solo para la página | Ninguna desde el navegador |
| `marked` | viaja al navegador | se ejecuta en el servidor |
| Código de cliente | toda la página | `Galeria` y `BotonAgregarAlCarrito` |

## 2. Qué datos llegan al navegador

`/n5/01-servidor-cliente/fuga` pasa el registro completo (`costoProveedor`, `notasInternas`) a un componente de cliente: ambos campos aparecen en el
HTML (carga RSC incluida) aunque la pantalla no los muestre. `/proyectada` pasa solo `{ nombre }` y no aparecen. La ficha del README
(`Galeria imagenes`, `BotonAgregarAlCarrito productoId precio`) tampoco los filtra. Las tres comprobaciones están en `e2e/n5-01.spec.ts`.

## 3. `server-only`

`scripts/server-only-falla.sh` importa `contexto.tsx` (que empieza con `import 'server-only'`) desde un componente `'use client'` y
ejecuta `next build`: la construcción falla con "You're importing a module that depends on "server-only"".

## 4. Cascada

`taller.test.ts`: `Promise.all` iguala el mayor de los dos tiempos (~100 ms) y la cascada los suma (~200 ms). La solución con `Suspense`
por sección es la página real `/n5/01-servidor-cliente/productos/1`: título, galería y descripción salen de inmediato y las reseñas (700 ms) se transmiten después.

## 5. Árbol de la página de producto y su frontera

```
layout (servidor)
└─ Proveedores            [CLIENTE]  (contexto del carrito y consultas; recibe children de servidor)
   └─ PaginaProducto      (servidor, async)
      ├─ h1
      ├─ Galeria          [CLIENTE]  necesita useState (carrusel)
      ├─ Descripcion      (servidor)  usa marked: la biblioteca no viaja
      ├─ BotonAgregarAlCarrito [CLIENTE] necesita onClick y el contexto del carrito
      └─ Suspense
         └─ Resenas       (servidor)  lenta: se transmite
```

La frontera está en las hojas que necesitan estado, eventos o contexto; cada una recibe solo primitivas (`imagenes`, `productoId`, `precio`).
`Proveedores` es de cliente pero recibe la aplicación como `children`, así el árbol de servidor no se contagia.
