# 05 — Suspense, límites de error y patrones heredados

## Suspense: la carga como parte de la estructura

**Problema.** Cada componente maneja su propio "cargando" con banderas, y la página se llena de indicadores que aparecen y desaparecen sin orden.

**Patrón.** Un límite de `Suspense` declara qué mostrar mientras lo que hay dentro no está listo. La carga deja de ser lógica de cada componente y pasa a ser estructura del árbol.

```tsx
export default function PaginaCliente({ params }: { params: Promise<{ id: string }> }) {
  return (
    <main>
      <Suspense fallback={<EsqueletoEncabezado />}>
        <EncabezadoCliente params={params} />          {/* rápido */}
      </Suspense>
      <Suspense fallback={<EsqueletoTabla />}>
        <PedidosDelCliente params={params} />          {/* lento: no bloquea al resto */}
      </Suspense>
    </main>
  );
}
```

Con renderizado en servidor, cada límite permite **transmitir** (*streaming*): el servidor envía primero lo que está listo y completa las demás partes a medida que resuelven. Colocar los límites es una decisión de diseño: agrupar lo que debe aparecer junto; separar lo lento de lo rápido.

## Límites de error: fallar por secciones

**Problema.** Un error en un componente desmonta toda la aplicación y deja la pantalla en blanco.

**Patrón.** Un límite de error atrapa los errores de renderizado de su subárbol y muestra una alternativa. El resto de la página sigue funcionando. (Equivale a un `try/catch` declarativo; es además Chain of Responsibility: el error sube hasta el límite más cercano.)

```tsx
// Con la biblioteca react-error-boundary
<ErrorBoundary
  FallbackComponent={({ error, resetErrorBoundary }) => (
    <div role="alert">
      <p>No pudimos cargar los pedidos.</p>
      <button type="button" onClick={resetErrorBoundary}>Reintentar</button>
    </div>
  )}
  onError={(error) => reportar(error)}
>
  <Suspense fallback={<EsqueletoTabla />}>
    <PedidosDelCliente />
  </Suspense>
</ErrorBoundary>
```

En Next.js, un archivo `error.tsx` define el límite de un segmento de ruta, y `not-found.tsx` el caso de recurso inexistente.

Los límites no atrapan errores en manejadores de eventos ni en código asíncrono fuera del renderizado: esos se manejan donde ocurren.

Distingue **errores esperados** (validación, sin permiso, no encontrado: son resultados, se muestran en su contexto) de **errores inesperados** (fallos: se reportan y se muestra una alternativa).

## Portales

Renderizan un componente en otro lugar del DOM (fuera de su contenedor) manteniéndolo en el árbol de React: diálogos, menús flotantes, avisos. Evitan problemas de recorte y de apilamiento. Para diálogos, el elemento nativo `<dialog>` resuelve además foco y fondo.

## Patrones heredados que encontrarás en código existente

Antes de los hooks (2019), la lógica se reutilizaba con dos técnicas. Hoy casi siempre se prefiere un hook, pero hay que saber leerlas.

| Patrón | Qué es | Estado actual |
|---|---|---|
| **Componente de orden superior (HOC)** | Función que recibe un componente y devuelve otro con algo añadido: `conAutenticacion(Pagina)` | Reemplazado por hooks. Es el patrón Decorator. Aún útil para envolver páginas enteras o en bibliotecas |
| **Propiedad de renderizado** | Componente que recibe una función y la llama con datos: `<Raton>{(posicion) => …}</Raton>` | Reemplazado por hooks para la lógica. Sigue vigente para personalizar el pintado (listas, tablas) |
| **Componentes de clase** | Con `this.state` y métodos de ciclo de vida | Solo necesarios para escribir un límite de error a mano |
| **Contenedor y presentación** | Separar datos de interfaz | Sigue siendo un buen criterio; hoy la frontera suele coincidir con servidor y cliente |

```tsx
// Antes: HOC
const PaginaProtegida = conSesion(Pagina);

// Hoy: hook
function Pagina() {
  const sesion = useSesion();
  if (!sesion) return <Ingreso />;
  /* ... */
}
```

Problemas de los HOC que llevaron a abandonarlos: propiedades que chocan entre varios envoltorios, árboles con muchas capas, tipos difíciles y origen poco claro de cada propiedad.

## Errores comunes

- Un solo `Suspense` en la raíz: toda la página espera a lo más lento.
- Demasiados límites: la página aparece a trozos, saltando.
- Indicadores giratorios donde un esqueleto con la forma del contenido evitaría saltos.
- Sin límites de error: pantalla en blanco.
- Un único límite de error global: cualquier fallo tumba todo.
- Tratar como excepción lo que es un resultado esperado.
- No reportar los errores atrapados.
- Escribir HOC nuevos donde un hook es más simple.

## Taller

1. En una página con tres secciones de distinta velocidad, coloca los límites de `Suspense` y justifica la agrupación.
2. Reemplaza indicadores giratorios por esqueletos y mide el salto de diseño (CLS).
3. Agrega límites de error por sección con botón de reintento y reporte.
4. Provoca un error en un manejador de eventos y comprueba que el límite no lo atrapa; manéjalo correctamente.
5. Convierte un HOC y un componente con propiedad de renderizado a hooks.
6. Clasifica los errores posibles de una pantalla en esperados e inesperados.

Rama `taller/rx-n3-05-suspense-errores`.

## Preguntas senior

1. ¿Cómo decides dónde poner los límites de `Suspense`?
2. ¿Qué atrapa y qué no atrapa un límite de error?
3. ¿Qué es la transmisión (*streaming*) y qué mejora?
4. ¿Por qué los hooks reemplazaron a los HOC?
5. Error esperado frente a inesperado: ¿cómo cambia el manejo?
