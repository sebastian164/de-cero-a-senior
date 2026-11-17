# Taller nivel-03/04: capa de datos y adaptadores. Conteos y decisiones

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-03-patrones/04-capa-de-datos-y-adaptadores/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.tsx` (10, con MSW y TanStack Query 5.107.1) y `e2e/n3-04.spec.ts` (red real del navegador). Código: `clientes.ts` (esquema, adaptador y acceso de clientes), `api-simulada.ts` (el "servidor"), `ui.tsx`; pedidos usa los archivos del README (`proyectos/react-next/n3-04-datos/`).

## Punto 2. Cambiar un campo del DTO: archivos tocados

Cambio de ejercicio: el servidor renombra `nombre_completo`. Se cuentan los archivos de la aplicación que contienen ese nombre (la API simulada es el servidor, no la aplicación): **1**, `clientes.ts` (en dos líneas: el esquema Zod, línea 7, y el adaptador, línea 22).
La prueba lo comprueba leyendo todos los archivos del taller. `ui.tsx`, los hooks y los componentes solo conocen `nombre`, que no cambia (el modelo queda con las mismas claves `documento`, `nombre`, `correo`, `activo`).
Sin la capa, el nombre del campo habría estado en cada componente que lo pinta.

## Punto 3. Un dato mal formado da un error claro

La API simulada responde `valor_centavos` como texto. `listarPedidos` lanza un `ZodError` cuyo primer problema es `path: [0, 'valor_centavos']` y mensaje "expected number, received string": dice qué elemento, qué campo y qué se esperaba, en la frontera, no tres componentes más adelante como `NaN` en pantalla.
Un estado numérico fuera de la unión (9) también lo rechaza el esquema antes de llegar al adaptador.

## Punto 4. Mutación optimista con reversa

Cancelar #101: el pedido desaparece antes de que el servidor responda y el estado final es "Pedido 101 cancelado". Con `fallarCancelacion` activo en #102: desaparece, aparece "No se pudo cancelar el pedido 102; se restauró la lista" y la lista vuelve a tener 4 pedidos.

## Punto 5. Una consulta, dos componentes, una petición

`ListaCancelable` y `ContadorPendientes` usan la misma clave de consulta. Medido de dos formas: con MSW, `estadoApi.peticiones` es **1**; en Chromium contra `next start`, las peticiones `GET /api/n3-04/pedidos` que salen en la red al abrir `/n3/04-datos` son **1** (`mediciones/n3-04.json`).

## Punto 6. Servidor o cliente, pantalla por pantalla (en la aplicación de referencia)

| Pantalla | Dónde se obtienen los datos | Justificación |
|---|---|---|
| `/n3/04-datos/servidor?estado=1` | servidor (`searchParams`, `await`) | lectura sin interacción; el HTML llega con los datos y sin JavaScript de consulta |
| `/n3/04-datos` (lista cancelable y contador) | cliente (TanStack Query) | hay mutación optimista, reversa, reintentos y datos compartidos entre dos componentes interactivos |
| `/n3/05-suspense/clientes/42` | servidor con `Suspense` | solo lectura; los dos bloques de distinta velocidad se transmiten por separado |
| `/n5/04-renderizado/inicio`, `producto/[id]` | servidor (estático / regenerado) | contenido público, indexable y compartido por todos |
| `/n5/04-renderizado/mi-cuenta` | servidor, dinámico | dato por persona, no se guarda ni se indexa |
Regla: servidor para lectura y primer pintado; cliente cuando el dato cambia por interacción o se comparte entre componentes que se actualizan solos.
