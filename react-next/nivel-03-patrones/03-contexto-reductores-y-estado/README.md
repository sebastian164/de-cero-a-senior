# 03 — Contexto, reductores y estado compartido

## El problema

Un dato lo necesitan componentes que están lejos entre sí en el árbol. Pasarlo por propiedades a través de cinco niveles (**taladro de propiedades**) acopla a todos los intermedios. Y cuando el estado tiene muchas transiciones posibles, varios `useState` sueltos permiten combinaciones que no deberían existir.

## Las piezas

| Pieza | Qué resuelve | Patrón que recuerda |
|---|---|---|
| **Contexto** | Entregar un valor a todo un subárbol sin pasarlo por propiedades | Inyección de dependencias |
| **Reductor (`useReducer`)** | Centralizar las transiciones del estado en una función pura | Command, State |
| **Proveedor + hook** | Encapsular ambos tras una API pequeña | Facade |
| **Almacén externo** (Zustand, Redux Toolkit) | Estado global con suscripción selectiva | Observer |

### Antes de usar contexto

Muchos taladros de propiedades se resuelven con **composición**: en lugar de pasar datos hacia abajo para que un nieto los use, se pasa el nieto ya armado como `children`.

### Reductores

`(estado, accion) => estadoNuevo`. Toda transición está en un lugar, es una función pura (se prueba con una línea) y las acciones describen **qué pasó**, no cómo cambiar el estado.

## Ejemplo: carrito

```tsx
// carrito/reductor.ts — puro, sin React
export interface EstadoCarrito {
  readonly lineas: readonly Linea[];
}

export type AccionCarrito =
  | { tipo: 'agregado'; producto: Producto }
  | { tipo: 'cantidadCambiada'; productoId: string; cantidad: number }
  | { tipo: 'quitado'; productoId: string }
  | { tipo: 'vaciado' };

export function reductorCarrito(estado: EstadoCarrito, accion: AccionCarrito): EstadoCarrito {
  switch (accion.tipo) {
    case 'agregado': {
      const existente = estado.lineas.find((l) => l.productoId === accion.producto.id);
      return {
        lineas: existente
          ? estado.lineas.map((l) => (l === existente ? { ...l, cantidad: l.cantidad + 1 } : l))
          : [...estado.lineas, { productoId: accion.producto.id, nombre: accion.producto.nombre, precio: accion.producto.precio, cantidad: 1 }],
      };
    }
    case 'cantidadCambiada':
      return {
        lineas: accion.cantidad <= 0
          ? estado.lineas.filter((l) => l.productoId !== accion.productoId)
          : estado.lineas.map((l) => (l.productoId === accion.productoId ? { ...l, cantidad: accion.cantidad } : l)),
      };
    case 'quitado':
      return { lineas: estado.lineas.filter((l) => l.productoId !== accion.productoId) };
    case 'vaciado':
      return { lineas: [] };
  }
}

export const totalCarrito = (estado: EstadoCarrito) =>
  estado.lineas.reduce((suma, l) => suma + l.precio * l.cantidad, 0);
```

```tsx
// carrito/proveedor.tsx
'use client';

const ContextoEstado = createContext<EstadoCarrito | null>(null);
const ContextoDespacho = createContext<React.Dispatch<AccionCarrito> | null>(null);

export function ProveedorDeCarrito({ children }: { children: React.ReactNode }) {
  const [estado, despachar] = useReducer(reductorCarrito, { lineas: [] });
  return (
    <ContextoDespacho value={despachar}>
      <ContextoEstado value={estado}>{children}</ContextoEstado>
    </ContextoDespacho>
  );
}

// API pública: los componentes no conocen el reductor ni las acciones
export function useCarrito() {
  const estado = useContext(ContextoEstado);
  if (!estado) throw new Error('Falta ProveedorDeCarrito');
  return { lineas: estado.lineas, total: totalCarrito(estado), cantidad: estado.lineas.length };
}

export function useAccionesDeCarrito() {
  const despachar = useContext(ContextoDespacho);
  if (!despachar) throw new Error('Falta ProveedorDeCarrito');
  return useMemo(() => ({
    agregar: (producto: Producto) => despachar({ tipo: 'agregado', producto }),
    quitar: (productoId: string) => despachar({ tipo: 'quitado', productoId }),
    vaciar: () => despachar({ tipo: 'vaciado' }),
  }), [despachar]);
}
```

El estado y las acciones van en contextos separados: un botón "agregar" que solo usa las acciones no se vuelve a renderizar cuando cambia el carrito.

## Límites del contexto

Cuando el valor de un contexto cambia, **todos** sus consumidores se renderizan, usen o no la parte que cambió. Para estado que cambia con frecuencia o que es grande, conviene un almacén externo con selectores:

```ts
const useAlmacenCarrito = create<EstadoCarrito & Acciones>()((set) => ({ /* ... */ }));
const cantidad = useAlmacenCarrito((estado) => estado.lineas.length);   // solo se renderiza si cambia esto
```

## Cuándo usar qué

| Situación | Herramienta |
|---|---|
| Estado de un componente | `useState` |
| Varios hijos cercanos | Elevar el estado |
| Datos que cruzan muchos niveles pero cambian poco (tema, sesión, idioma) | Contexto |
| Transiciones complejas | `useReducer` |
| Estado global que cambia a menudo | Almacén externo con selectores |
| Datos del servidor | Componentes de servidor o biblioteca de datos; **no** un almacén |
| Filtros, página, pestaña | La URL |

## Errores comunes

- Contexto para evitar pasar una propiedad un solo nivel.
- Un contexto gigante con toda la aplicación.
- Valor del contexto creado como objeto nuevo en cada renderizado.
- Reductores con efectos (peticiones, `localStorage`) o que modifican el estado en el sitio.
- Acciones tipo "setter" (`setLineas`) en lugar de hechos (`agregado`).
- Guardar datos del servidor en un almacén global y sincronizarlos a mano.
- Proveedores en la raíz que obligan a marcar todo como cliente; colócalos lo más abajo posible.

## Taller

1. Implementa el carrito del ejemplo y prueba el reductor sin React, caso por caso.
2. Agrega persistencia y cupones sin tocar los componentes.
3. Comprueba con las herramientas de React qué se renderiza al agregar un producto, con un contexto único y con dos separados.
4. Reescríbelo con un almacén externo y compara.
5. Resuelve un taladro de propiedades de un proyecto tuyo con composición, sin contexto.

Rama `taller/rx-n3-03-contexto`.

## Preguntas senior

1. ¿Cuándo el contexto es la herramienta correcta y cuándo no?
2. ¿Qué ventaja tiene un reductor sobre varios `useState`?
3. ¿Por qué separar el estado de las acciones en dos contextos?
4. ¿Por qué los datos del servidor no pertenecen a un almacén global?
