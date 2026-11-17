# Taller nivel-04/02: registro de una clínica. Resultados y hallazgos

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-04-clean-code-y-testing/02-formularios-y-validacion/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.tsx` (Vitest) y `e2e/n4-02.spec.ts` (Chromium **sin JavaScript**, contra `next start`). Código: `esquema.ts`, `acciones.ts`, `formulario-accion.tsx`, `formulario-biblioteca.tsx`.

## Punto 2. La acción de servidor funciona con JavaScript desactivado (comprobado)

`e2e/n4-02.spec.ts` abre `/n4/02-formularios` en un contexto con `javaScriptEnabled: false` y envía los formularios de forma nativa (HTML + `POST` + acción de servidor):

| Comprobación | Resultado |
|---|---|
| El HTML del servidor trae los formularios con `method="POST"` | sí (al menos dos) |
| (a) "Agregar contacto" devuelve una fila más y conserva lo escrito | pasa: 1 fila a 2, y "Marta Gómez" y "Luis Gómez" siguen en sus campos |
| (b) Enviar vacío muestra los errores por campo | pasa: nombre, documento, correo y autorización |
| (c) Un registro válido termina en "Paciente registrado: Marta Gómez" | pasa |
| `FormularioRegistro` del README sin JavaScript: errores y valores devueltos | pasa: al menos dos alertas y los valores escritos se conservan |

### Hallazgo: un formulario de la biblioteca vaciaba el HTML de los demás

Sin aislar ese formulario, con `next start` y JavaScript desactivado la página solo traía un `<form>`; los de acción de servidor no estaban en el HTML. Causa (aislada quitando formularios de uno en uno):
`FormularioClinicaBiblioteca` (React Hook Form con `useFieldArray`) hace que **todo el prerender de la página se aborte** con `cacheComponents: true`; los demás `<Suspense>` quedan como fallback "Cargando formulario…" y su contenido llega solo por JavaScript. Es una limitación de Next.js 16.4.6 con Cache Components, no del código de la acción.
Corrección: `src/solo-cliente.tsx` carga el formulario de la biblioteca solo en el navegador (`next/dynamic` con `ssr: false`; ese formulario necesita JavaScript de todos modos) y los de acción vuelven al HTML con su `action`.
Además se encontró y corrigió un defecto del esquema: `.max(hoy(), …)` evaluaba `new Date()` al **cargar el módulo**, así que "hoy" quedaba congelado al fecha del primer arranque; ahora es `.refine((f) => f <= hoy(), …)`, que lee la fecha al validar.

## Puntos 1, 6 y 7. Esquema y acción sin React

El esquema se prueba sin React (válido, tabla de inválidos, menor sin acudiente, contacto con el mismo teléfono de la persona, `desdeFormData` con nombres `contactos.N.campo`). La acción valida **siempre**: un `FormData` inválido llamado directamente devuelve los errores y `cantidadPacientes()` no cambia; un objeto manipulado (sin `aceptaDatos`, cinco contactos) se rechaza con "Máximo 3 contactos".

## Puntos 3, 4 y 5. Biblioteca

Validación al salir del campo (`mode: 'onBlur'`), contactos entre 1 y 3 (el botón se deshabilita en los extremos), errores del servidor asignados con `setError` y foco en el primer campo con error (`setFocus`; con el envío vacío el foco va al primer campo inválido).
La versión de biblioteca no existe en el HTML de servidor (necesita JavaScript); la de acción de servidor sí. Ese contraste es el criterio para elegir entre ellas.
