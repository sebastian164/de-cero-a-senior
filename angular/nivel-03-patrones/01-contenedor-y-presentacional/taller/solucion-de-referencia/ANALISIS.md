# Taller n3-01 — Contenedor y presentación

| # | Solución | Prueba |
|---|---|---|
| 1 | `contenedor.ts` (`PaginaCursos`, único que inyecta) y tres de presentación en `ui.ts`: `FiltroCategorias`, `TarjetaCurso`, `ResumenSeleccion` | `presentacion.spec.ts` › contenedor |
| 2 | Pruebas de los tres de presentación sin servicios: solo `inputs`, `on` y proyección | `presentacion.spec.ts` |
| 3 | `ejemplos.ts`: página `/n3-01/taller/ejemplos` con datos fijos | E2E la abre y guarda captura |
| 4 | Taladro de propiedades eliminado con proyección: el botón "Elegir" lo crea el contenedor y lo proyecta en `[acciones]` de la tarjeta | La tarjeta no tiene entradas `puedeInscribirse` ni salidas `alternar` |

El criterio "¿podría mostrarse solo con datos de ejemplo?" se cumple literalmente: la página de ejemplos monta los tres componentes sin proveedores.
