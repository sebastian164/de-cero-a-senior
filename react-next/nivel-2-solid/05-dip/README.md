# 05 — DIP en React

Explicación completa: [DIP en la ruta Java](../../../java/nivel-2-solid/05-dip/).

## Idea

React no trae un contenedor de inyección de dependencias, pero el principio es el mismo: la lógica de tu aplicación no debería conocer `fetch`, `localStorage` ni el SDK de un proveedor. Debería depender de un contrato, y que alguien, desde afuera, le entregue quién lo cumple.

## Formas de inyectar en React

| Mecanismo | Cómo | Cuándo |
|---|---|---|
| **Parámetros de función** | La función recibe sus dependencias | Lógica pura y casos de uso. La más simple y la mejor por defecto |
| **Propiedades** | El componente recibe la función o el objeto | Componentes de presentación |
| **Contexto** | Un proveedor arriba en el árbol; los componentes lo leen | Dependencias usadas en muchos lugares (cliente de API, analítica, reloj) |
| **Módulo de composición** | Un archivo que ensambla las implementaciones reales | Código de servidor en Next.js |
| **Hooks como fachada** | El componente usa `usePedidos()` y no sabe qué hay debajo | Ocultar la biblioteca de datos |

## Antes

```tsx
'use client';
function BotonAgregar({ producto }: { producto: Producto }) {
  function agregar() {
    const carrito = JSON.parse(localStorage.getItem('carrito') ?? '[]');     // navegador
    carrito.push({ ...producto, agregadoEn: Date.now() });                   // reloj
    localStorage.setItem('carrito', JSON.stringify(carrito));
    (window as any).gtag('event', 'add_to_cart', { id: producto.id });       // SDK de un tercero
    fetch('/api/carrito', { method: 'POST', body: JSON.stringify(producto) });
  }
  return <button onClick={agregar}>Agregar</button>;
}
```

No se puede probar sin navegador, y cambiar de proveedor de analítica obliga a tocar un botón.

## Después

```ts
// carrito/puertos.ts — contratos definidos por la lógica
export interface AlmacenDeCarrito {
  leer(): LineaCarrito[];
  guardar(lineas: LineaCarrito[]): void;
}
export interface Analitica {
  productoAgregado(productoId: string): void;
}
export interface Dependencias {
  almacen: AlmacenDeCarrito;
  analitica: Analitica;
  ahora: () => number;
}

// carrito/agregar.ts — lógica pura respecto a sus dependencias
export function agregarAlCarrito(producto: Producto, { almacen, analitica, ahora }: Dependencias): LineaCarrito[] {
  const lineas = [...almacen.leer(), { ...producto, agregadoEn: ahora() }];
  almacen.guardar(lineas);
  analitica.productoAgregado(producto.id);
  return lineas;
}
```

```tsx
// carrito/dependencias.tsx — inyección por contexto
const ContextoDependencias = createContext<Dependencias | null>(null);

export function ProveedorDeDependencias({ valor, children }: { valor: Dependencias; children: React.ReactNode }) {
  return <ContextoDependencias value={valor}>{children}</ContextoDependencias>;
}

export function useDependencias(): Dependencias {
  const dependencias = useContext(ContextoDependencias);
  if (!dependencias) throw new Error('Falta ProveedorDeDependencias');
  return dependencias;
}

// En el componente
function BotonAgregar({ producto }: { producto: Producto }) {
  const dependencias = useDependencias();
  return <button type="button" onClick={() => agregarAlCarrito(producto, dependencias)}>Agregar</button>;
}

// Ensamblaje real, una vez, en la raíz de la parte de cliente
<ProveedorDeDependencias valor={{ almacen: almacenLocal, analitica: analiticaGoogle, ahora: Date.now }}>
```

```ts
// En una prueba: sin navegador ni proveedores
const guardados: LineaCarrito[][] = [];
const lineas = agregarAlCarrito(unProducto(), {
  almacen: { leer: () => [], guardar: (l) => guardados.push(l) },
  analitica: { productoAgregado: () => {} },
  ahora: () => 1_700_000_000_000,
});
expect(lineas).toHaveLength(1);
```

### En el servidor (Next.js)

No hay contexto de React en el código de servidor. Se inyecta por parámetros y se ensambla en un módulo:

```ts
// pedidos/casos-de-uso.ts
export const crearCancelarPedido = (repositorio: RepositorioPedidos, reloj: () => Date) =>
  async (pedidoId: string, usuarioId: string) => { /* reglas */ };

// pedidos/composicion.ts — el único archivo que conoce las implementaciones
export const cancelarPedido = crearCancelarPedido(repositorioPostgres, () => new Date());
```

## Cuándo no

`fetch` dentro de la capa de datos es correcto: esa capa es el detalle. No hace falta inyectar todo: abstrae lo externo, lo no determinista y lo que podría cambiar. Las bibliotecas de interfaz y el propio React se usan directamente.

## Errores comunes

- `fetch`, `localStorage` y `window` directamente en componentes.
- Importar el SDK de un proveedor desde decenas de archivos.
- Contextos usados como localizador global de todo.
- Contratos que copian la API del proveedor.
- Simular módulos enteros en las pruebas porque la dependencia no se puede sustituir de otra forma.

## Taller

1. Lista las dependencias ocultas de un componente tuyo.
2. Extrae la lógica a una función que reciba sus dependencias y pruébala sin navegador.
3. Provee las implementaciones por contexto y cámbialas en una prueba de componente.
4. En Next.js, crea un módulo de composición para un caso de uso de servidor.
5. Indica qué decidiste no abstraer.

Rama `taller/rx-n2-05-dip`.

## Preguntas senior

1. ¿Cómo se inyectan dependencias en React sin un contenedor?
2. ¿Cuándo usar parámetros, propiedades o contexto?
3. ¿Por qué simular módulos completos en las pruebas es una señal de diseño?
4. ¿Cómo inyectas dependencias en código de servidor de Next.js?
