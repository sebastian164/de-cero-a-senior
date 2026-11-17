# Taller n5-02 — Gestión de estado

Versiones: Angular 22.2, `@ngrx/signals` 22.0.1. Documentos: `ESTADO.md` (puntos 1-3) y `CRITERIO.md` (punto 6).

| # | Solución | Prueba |
|---|---|---|
| 1 | Clasificación en `ESTADO.md` | |
| 2 | Estado, página y pestaña en la URL: `pedidos-url.pagina.ts` (entradas con `withComponentInputBinding`) | `estado.spec.ts` y E2E (cambiar de pestaña y de página cambia la URL y conserva los demás parámetros) |
| 3 | `duplicado.ts`: la copia (`cantidad`) se desincroniza; la versión con `computed` no | `estado.spec.ts` |
| 4 | Carrito con servicio (`carrito.servicio.ts`) y con SignalStore (`carrito.store.ts`); **el mismo juego de 5 pruebas** corre contra ambos (`describe.each`) | `carrito.spec.ts`; E2E: los dos contadores coinciden |
| 5 | `CacheServidor`: clave, vigencia, deduplicación, revalidación mientras sirve lo guardado, invalidación tras una mutación, y descarte de respuestas viejas | `cache-servidor.spec.ts` (6 pruebas) y E2E: dos listas, una lectura; crear un producto, una lectura más y las dos listas se actualizan |
| 6 | `CRITERIO.md` | |

## Punto 4: comparación

| | Servicio con señales | SignalStore |
|---|---|---|
| Líneas del archivo | 20 | 23 |
| Clases / conceptos nuevos | Ninguno: `signal`, `computed` | `signalStore`, `withState`, `withComputed`, `withMethods`, `patchState` |
| Encapsulación | `asReadonly()` a mano | La da el diseño: el estado solo cambia con `patchState` dentro de `withMethods` |
| Prueba | Idéntica (`TestBed.inject`) | Idéntica; se provee igual |
| Qué suma a partir de muchos almacenes | Nada: cada uno es distinto | Características reutilizables (`signalStoreFeature`) |

Conclusión medida: para un almacén, las dos versiones tienen un tamaño parecido y las mismas pruebas; la biblioteca solo paga con muchos almacenes.

## Hallazgos sobre el README

- **Error en el ejemplo "El filtro que debía estar en la URL":** con `input(1, { transform: numberAttribute })` y `input('todos')`, abrir la página sin parámetros enviaba `estado=undefined&pagina=NaN` (el enlace de entradas del router escribe `undefined` y no respeta el valor por defecto). Se corrigió el README con `transform`s que devuelven el valor por defecto (`ejemplo.spec.ts` lo comprueba).
- Los parámetros solo se enlazan al componente de la ruta: si la página contenedora los pasa a una hija, el contenedor debe declararlos (se hizo así en `pagina.component.ts`).
- `resource`/`httpResource` no son una caché compartida (como dice el README): por eso el taller escribe `CacheServidor`.
