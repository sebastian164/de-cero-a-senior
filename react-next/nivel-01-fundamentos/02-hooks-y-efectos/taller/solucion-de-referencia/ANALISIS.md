# Taller nivel-01/02: hooks y efectos. Lo que había que anotar y explicar

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-01-fundamentos/02-hooks-y-efectos/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.tsx`. El "antes" con efectos innecesarios está en `antes/resumen-con-efectos.tsx`; el "después" en `componentes.tsx`.

## Punto 3. Efectos eliminados y qué reemplazó a cada uno

| Efecto del "antes" (`ResumenCarritoConEfectos`) | ¿Sincroniza con algo externo? | Qué lo reemplazó |
|---|---|---|
| `useEffect(() => setTotal(productos.reduce(…)), [productos])` | No: es un valor derivado | `const total = productos.reduce(…)` al renderizar (sin estado, sin segundo renderizado) |
| `useEffect(() => { setCantidad(productos.length); onCambio(productos.length) }, …)` | No: derivado y aviso al padre | `productos.length` al renderizar, y `onCambio(...)` dentro del manejador `agregar` que cambia el estado |
| `useEffect(() => { document.title = … }, [productos.length])` | **Sí**: el título del documento | Se queda: es el único efecto legítimo |

El "antes" renderiza dos veces por cada cambio (una con el valor viejo y otra con el efecto aplicado) y mostraba un instante con cantidad y total desfasados. Prueba: "llama al padre en el mismo manejador" (el padre recibe 1, la pantalla dice "1 productos · 18000" y el título es "Carrito (1)").

## Punto 4. La carrera entre peticiones

Se pide "c", "ca", "caf" en ese orden con retardos inversos (la de "c" tarda más). Resultado observado:

| Versión | Resultados mostrados al terminar | Peticiones |
|---|---|---|
| Sin cancelar (`BuscadorConCarrera`) | "Café de Huila, Café descafeinado, **Cacao en polvo**": los de "c", que llegaron últimos y pisaron a los de "caf" | `['c', 'ca', 'caf']` |
| Con `AbortController` (`BuscadorSinCarrera`) | "Café de Huila, Café descafeinado": los de "caf" | las anteriores se cancelan en la limpieza del efecto |

## Punto 5. `key` en lugar de un efecto de reinicio

Al cambiar de cliente, `<FormularioCliente key={cliente.id} …/>` desmonta el formulario y monta uno nuevo con el nombre del cliente nuevo; no hace falta un efecto que copie la propiedad al estado. Prueba: "al cambiar de cliente, el formulario arranca con el nombre nuevo".

## Punto 6. Qué problema revela el doble montaje del modo estricto

En desarrollo, el modo estricto monta, desmonta y vuelve a montar cada componente. Sin limpieza, una suscripción se abre dos veces y la primera nunca se cierra: la prueba cuenta **2** conexiones abiertas sin limpieza y **1** con limpieza.
El doble montaje no es un defecto de React: revela efectos que no son simétricos (abrir sin cerrar), que en producción se manifestarían como fugas o como mensajes duplicados al desmontar y volver a entrar a la pantalla.
