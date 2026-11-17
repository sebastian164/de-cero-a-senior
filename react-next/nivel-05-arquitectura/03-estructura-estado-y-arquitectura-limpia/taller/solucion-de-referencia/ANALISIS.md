# Taller nivel-05/03: análisis

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-05-arquitectura/03-estructura-estado-y-arquitectura-limpia/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Código real: `proyectos/funcionalidades/pedidos/` (la estructura del README, con PostgreSQL 17 real en el puerto 49540) y rutas delgadas en
`src/app/(principal)/n5/03-arquitectura/`. Pruebas: `taller.test.ts` (Vitest) y `e2e/n5-03.spec.ts` (Chromium contra `next start`).

## 1. Estructura y límites
La ruta `pedidos/page.tsx` solo re-exporta la página del README (tres líneas de lógica: leer `estado`, consultar y pintar). El código vive en
`funcionalidades/pedidos/{dominio,aplicacion,datos,acciones,ui}`. Las reglas de dependencia están en `eslint.config.mjs` con `no-restricted-imports`
y se prueban en `taller.test.ts` (grupo "punto 1"): el dominio no importa React ni Next ni `datos`/`acciones`/`ui`; `compartido` no importa
funcionalidades; una funcionalidad entra a otra solo por su `index.ts`. Cada regla se prueba con un caso que la viola y uno que la cumple.

## 2. Clasificación del estado (pantalla de pedidos)

| Estado | Tipo | Dónde vive |
|---|---|---|
| Lista de pedidos | De servidor | Componente de servidor + PostgreSQL (sin almacén global) |
| Filtro por estado | De URL | `?estado=` (`FiltroDeEstado` navega con `router.replace`); recargar lo conserva |
| Mensaje de resultado de cancelar | Interfaz local | `useState` en `ListaDePedidos` |
| Cancelación en curso | Interfaz local | `useTransition` |
| Usuario de la sesión | De servidor | Cookie leída en el servidor (`exigirUsuario`) |

Se eliminó cualquier almacén global: no queda estado compartido de cliente que lo justifique.

## 3. Caso de uso con reglas reales
`crearCancelarPedido(repositorio, reloj)`: pertenencia, plazo de 24 horas y estado. Se prueba sin Next.js ni base de datos con un repositorio en memoria y un reloj fijo
(5 pruebas, incluido el borde inclusivo de 24 h).

## 4. Segundo adaptador
`repositorio-memoria.ts` implementa el mismo puerto. `composicion-seleccionable.ts` elige el adaptador en un solo lugar. Las mismas 4 pruebas de contrato pasan contra el adaptador en memoria y contra PostgreSQL real.

## 5. Una pantalla que solo lista datos
Una página que solo consulta y pinta (por ejemplo, un catálogo de solo lectura) no necesita puertos, casos de uso ni dominio: no hay reglas que proteger ni
detrás de qué abstraer, y cada capa añadiría archivos y saltos sin cambiar ningún comportamiento. Basta una función de acceso (`datos/consultas.ts`) y un componente.
El criterio para añadir capas es que existan reglas de negocio propias que probar aisladas o más de un origen de datos; ninguno aplica allí.
