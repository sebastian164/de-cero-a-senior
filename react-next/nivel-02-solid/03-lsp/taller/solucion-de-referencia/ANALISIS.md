# Taller nivel-02/03: LSP. Hallazgos

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-02-solid/03-lsp/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.tsx` (15 pruebas). Código: `envoltorios.tsx` (`Boton`, `Campo`, `Enlace`), `repositorio.ts` (adaptador HTTP y variante en memoria con fallo).

## Punto 3. Un componente que ignora lo que acepta o debería aceptar

El `Campo` «Antes» del README (`proyectos/react-next/n2-03-lsp/campo-antes.tsx`) declara solo `etiqueta`, `valor` y `alCambiar`. Lo que la prueba comprueba:

| Qué se intenta | Resultado |
|---|---|
| `<CampoAntes … disabled />` | no compila (`@ts-expect-error` en la prueba). Y si se fuerza, el campo **no queda deshabilitado**: la propiedad se pierde en silencio |
| asociar la etiqueta | `getByLabelText('Correo')` no encuentra nada: la etiqueta es un `<span>`, no un `<label for>`; un lector de pantalla no anuncia el campo |
| `name`, `required`, `autoComplete`, `onBlur`, `ref`, `aria-*` | no existen en su contrato, así que no sirve para un formulario nativo, una acción de servidor ni una biblioteca de formularios |

El `Campo` «Después» (`React.ComponentProps<'input'>`) deshabilita el campo, asocia la etiqueta y entrega la `ref` al `<input>`. Es el criterio de sustitución: donde iría un `<input>`, va un `Campo`.

## Punto 2. Batería de contrato y qué garantiza cada implementación

Una sola batería (`contratoRepositorioPedidos`) se ejecuta contra dos implementaciones: el adaptador HTTP con red simulada (MSW) y la implementación en memoria. Las cuatro garantías del contrato:

| Garantía | HTTP (MSW) | En memoria |
|---|---|---|
| devuelve los pedidos (ids como texto) | convierte `id: 1` en `'1'` | los guarda ya como texto |
| devuelve `[]` si no hay, nunca `null` | el servidor responde `null` y el adaptador lo convierte en `[]` (también 204) | `[]` |
| lanza `ErrorDeRed` si no puede consultar | 503, respuesta rara o `fetch` que falla | `pedidosEnMemoriaConFallo(semilla, { falla: true })`: el fallo lo produce la implementación, no la prueba |
| devuelve una copia | construye objetos nuevos | `[...iniciales]` |

Detalle: el escenario de fallo de la implementación en memoria no es un objeto escrito dentro de la prueba; ese caso lo ejecuta `pedidosEnMemoriaConFallo` (en `repositorio.ts`), de modo que "lanza ErrorDeRed" ejerce la implementación bajo prueba.
La implementación en memoria del README (`pedidosEnMemoria`) por sí sola **no puede fallar**; eso es una diferencia real de contrato que la batería habría ocultado.

## Punto 4. En qué se diferencian los simulacros del servidor real

| Lo que el simulacro de las pruebas hace | Lo que el servidor real puede hacer y la prueba no ve |
|---|---|
| MSW responde en `delay(30)` ms, siempre | latencias variables, tiempos de espera agotados, respuestas fuera de orden |
| el escenario "vacío" responde `null` (lo que se sabe que hace) | respuestas con otra forma (objeto con paginación, HTML de un proxy, cuerpo truncado); el adaptador solo defiende lo conocido: `'respuesta inesperada'` |
| "falla" es siempre 503 | 401 con la sesión vencida, 429 con `Retry-After`, fallos intermitentes que se recuperan |
| los ids numéricos del simulacro | ids que cambian de tipo entre versiones del servidor |
| la implementación en memoria no tiene red | orden de los pedidos, paginación y consistencia (leer tras escribir) que solo existen con un servidor real |

Por eso el contrato exige lo observable (`[]`, `ErrorDeRed`, copia) y no lo incidental, y por eso una prueba de integración contra el servicio real (o su contenedor) sigue siendo necesaria.
En nivel-05/03 el mismo contrato corre contra la implementación en memoria y contra PostgreSQL real.
