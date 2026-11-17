# Taller n1-01 — Lista de tareas

| Criterio | Dónde |
|---|---|
| 1. Lista (coordina) y elemento (presenta) con `input()`/`output()` | `lista-tareas.component.ts`, `elemento-tarea.component.ts` |
| 2. Servicio en memoria | `tareas.service.ts` (señal privada, `asReadonly()`) |
| 3. Agregar, marcar, eliminar, filtrar | Probado en `lista-tareas.spec.ts` y en la prueba E2E `e2e/n1.spec.ts` |
| 4. Contador derivado | `pendientes = computed(...)` en el servicio |
| 5. Dos rutas | `rutas.ts`: `''` y `':id'`, con `withComponentInputBinding()` |
| 6. Lógica pura probada sin Angular | `tareas.ts` + `tareas.spec.ts` (no importa `@angular/*`) |

Lógica que quedó pura: agregar (con el cálculo del id y el recorte del título), alternar, eliminar, filtrar y contar. El componente solo conecta eventos con el servicio, y el servicio solo guarda la señal y llama a esas funciones.
