# 01 — Arquitectura de componentes de servidor y de cliente

## Idea

En una aplicación Next.js hay dos entornos: el servidor, que tiene los datos y los secretos, y el navegador, que tiene la interactividad. La decisión de arquitectura más importante es por dónde pasa la frontera entre ambos. Bien puesta, se envía poco JavaScript y los datos llegan sin cascadas; mal puesta, se pierde todo eso sin notarlo.

## Lo esencial

### El modelo

- Los componentes son de **servidor por defecto**. Se ejecutan en el servidor, pueden ser `async`, y su código no se envía al navegador.
- `'use client'` marca una **frontera**: ese módulo y todo lo que importa pasa a formar parte del paquete de cliente.
- Los componentes de cliente también se renderizan en el servidor para producir el HTML inicial; después se hidratan en el navegador.

### Qué va en cada lado

| Servidor | Cliente |
|---|---|
| Consultar datos | Estado y efectos |
| Usar secretos y claves | Manejadores de eventos |
| Dependencias pesadas que solo producen HTML (Markdown, resaltado) | APIs del navegador |
| Contenido estático | Animaciones e interacción |

### Reglas de la frontera

1. **Lo que cruza del servidor al cliente debe ser serializable:** texto, números, objetos planos, arreglos, fechas, promesas. No funciones ni instancias de clases. La excepción son las acciones de servidor, que sí pueden pasarse.
2. **Un componente de cliente no puede importar uno de servidor,** pero sí puede **recibirlo** como `children` u otra propiedad.
3. **Todo lo que se pasa a un componente de cliente llega al navegador.** Proyecta: pasa solo los campos necesarios.
4. **La frontera va lo más abajo posible.** La interactividad suele estar en las hojas: un botón, un campo, un menú.

### Protección del código de servidor

El paquete `server-only` hace fallar la compilación si un módulo de servidor termina importado desde el cliente. Úsalo en todo módulo que toque la base de datos o los secretos.

## Antes

```tsx
'use client';                                            // toda la página es de cliente

export default function PaginaProducto({ params }: { params: { id: string } }) {
  const [producto, setProducto] = useState<Producto | null>(null);
  const [resenas, setResenas] = useState<Resena[]>([]);

  useEffect(() => {
    fetch(`/api/productos/${params.id}`).then((r) => r.json()).then(setProducto);
    fetch(`/api/productos/${params.id}/resenas`).then((r) => r.json()).then(setResenas);
  }, [params.id]);

  if (!producto) return <p>Cargando…</p>;
  return (/* descripción en Markdown, galería, reseñas, botón de comprar */);
}
```

El HTML inicial no tiene contenido (malo para la carga y para los buscadores), las peticiones salen después de descargar y ejecutar JavaScript, hacen falta endpoints solo para alimentar la página, y la biblioteca de Markdown viaja al navegador.

## Después

```tsx
// app/productos/[id]/page.tsx — servidor
export default async function PaginaProducto({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const producto = await obtenerProducto(id);            // directo, sin endpoint intermedio
  if (!producto) notFound();

  return (
    <main>
      <h1>{producto.nombre}</h1>
      <Galeria imagenes={producto.imagenes} />            {/* cliente: carrusel */}
      <Descripcion markdown={producto.descripcion} />      {/* servidor: la biblioteca no viaja */}
      <BotonAgregarAlCarrito productoId={producto.id} precio={producto.precio} />   {/* cliente */}
      <Suspense fallback={<EsqueletoResenas />}>
        <Resenas productoId={producto.id} />              {/* servidor, se transmite cuando esté lista */}
      </Suspense>
    </main>
  );
}

// productos/datos.ts
import 'server-only';
export async function obtenerProducto(id: string): Promise<Producto | null> { /* consulta a la base */ }
```

```tsx
// productos/ui/boton-agregar.tsx
'use client';
export function BotonAgregarAlCarrito({ productoId, precio }: { productoId: string; precio: number }) {
  const { agregar } = useAccionesDeCarrito();
  return <button type="button" onClick={() => agregar({ productoId, precio })}>Agregar al carrito</button>;
}
```

Solo el carrusel y el botón son código de cliente.

### Proveedores de contexto

Los proveedores (tema, carrito, cliente de consultas) son componentes de cliente. Se envuelven en un componente propio y se colocan en el diseño, pasando la aplicación como `children`: así el contenido sigue pudiendo ser de servidor.

```tsx
// app/layout.tsx — servidor
export default function Diseno({ children }: { children: React.ReactNode }) {
  return <html lang="es"><body><Proveedores>{children}</Proveedores></body></html>;
}
```

## Evitar cascadas

```tsx
// Cascada: la segunda espera a la primera sin necesitarla
const cliente = await obtenerCliente(id);
const pedidos = await obtenerPedidos(id);

// En paralelo
const [cliente, pedidos] = await Promise.all([obtenerCliente(id), obtenerPedidos(id)]);

// O mejor: cada sección pide lo suyo y se transmite con su propio Suspense
```

## Cuándo no usar Next.js ni componentes de servidor

Una aplicación totalmente interactiva tras inicio de sesión (un editor, un tablero muy dinámico) saca poco provecho del renderizado en servidor: una aplicación de cliente con Vite puede ser más simple. No es una decisión por defecto.

## Errores comunes

- `'use client'` en diseños o páginas enteras.
- Obtener en el cliente lo que el servidor podía entregar ya resuelto.
- Crear endpoints solo para que la propia página los consuma.
- Pasar registros completos de la base de datos a componentes de cliente.
- Importar módulos de servidor desde el cliente sin protección.
- Cascadas de `await`.
- Proveedores que convierten todo el árbol en cliente por importar páginas directamente.
- Esperar que un componente de servidor reaccione a un evento.

## Taller

1. Toma una página totalmente de cliente y conviértela: servidor por defecto, cliente solo en las hojas interactivas. Mide el JavaScript enviado antes y después.
2. Inspecciona la respuesta de la página y lista qué datos llegan al navegador. Recorta lo que no debería estar.
3. Agrega `server-only` a los módulos de datos y provoca el error importándolos desde el cliente.
4. Encuentra una cascada y resuélvela de dos formas: `Promise.all` y `Suspense` por sección.
5. Dibuja el árbol de componentes de una página marcando la frontera y justifícala.

Rama `taller/rx-n5-01-servidor-cliente`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué significa realmente `'use client'`?
2. ¿Cómo incluyes un componente de servidor dentro de uno de cliente?
3. ¿Qué puede y qué no puede cruzar la frontera?
4. ¿Qué riesgo de seguridad introduce la frontera y cómo lo mitigas?
5. ¿Cuándo una aplicación de cliente pura es mejor opción?
