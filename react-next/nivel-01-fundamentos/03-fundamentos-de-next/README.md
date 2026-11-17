# 03 — Fundamentos de Next.js

Next.js evoluciona rápido y varios detalles (sobre todo el modelo de caché y algunos nombres de archivos especiales) han cambiado entre versiones mayores. Esta ruta usa el enrutador de aplicación (*App Router*) y explica los conceptos; confirma la sintaxis exacta en la documentación de tu versión (nextjs.org/docs).

## Idea

React se ocupa de la interfaz. Next.js agrega todo lo que una aplicación real necesita alrededor: rutas, renderizado en el servidor, obtención de datos, optimización y despliegue. Su idea central hoy es que **los componentes se ejecutan en el servidor por defecto**, y solo las partes interactivas se envían al navegador.

## Lo esencial

### Rutas basadas en carpetas

```
app/
├── layout.tsx              diseño raíz: envuelve a todas las páginas
├── page.tsx                /
├── pedidos/
│   ├── layout.tsx          diseño compartido por /pedidos/*
│   ├── page.tsx            /pedidos
│   ├── loading.tsx         interfaz mientras carga (Suspense)
│   ├── error.tsx           límite de error de este segmento
│   ├── not-found.tsx       404 de este segmento
│   └── [id]/
│       └── page.tsx        /pedidos/123
└── api/
    └── pedidos/route.ts    endpoint HTTP
```

Si el segmento tiene `loading.tsx`, la respuesta ya empezó a enviarse con estado 200 cuando se ejecuta `notFound()`: Next muestra `not-found.tsx` y añade `noindex`, pero el estado HTTP no es 404 (comprobado con Next.js 16.4.6).

Cada carpeta es un segmento de la URL. Los archivos especiales definen qué se muestra en cada situación.

### Componentes de servidor y de cliente

| | Componente de servidor (por defecto) | Componente de cliente (`'use client'`) |
|---|---|---|
| Se ejecuta en | El servidor | El servidor (para el HTML inicial) y el navegador |
| Puede | Leer la base de datos, usar secretos, `await` directo | Estado, efectos, eventos, APIs del navegador |
| No puede | Estado, efectos, manejadores de eventos | Acceder a recursos del servidor |
| Su código | No se envía al navegador | Se envía al navegador |

Regla: **servidor por defecto; cliente solo donde hay interactividad**, y lo más abajo posible en el árbol.

### Obtención de datos

En un componente de servidor se obtienen los datos directamente, con `async`/`await`. Sin `useEffect`, sin estados de carga manuales.

### Navegación

`<Link href="…">` para navegar sin recargar; `useRouter` y `usePathname` en componentes de cliente; `redirect()` y `notFound()` en el servidor.

### Mutaciones

Las **acciones de servidor** (`'use server'`) son funciones que se ejecutan en el servidor y se pueden llamar desde un formulario o un componente de cliente. Tras modificar datos, se indica qué debe revalidarse.

## Ejemplo

```tsx
// app/pedidos/page.tsx — componente de servidor
import { listarPedidos } from '@/pedidos/datos';
import { ListaPedidos } from '@/pedidos/ui/lista-pedidos';

export default async function PaginaPedidos() {
  const pedidos = await listarPedidos();          // se ejecuta en el servidor
  return (
    <main>
      <h1>Pedidos</h1>
      <ListaPedidos pedidos={pedidos} />
    </main>
  );
}
```

```tsx
// pedidos/ui/boton-cancelar.tsx — la única parte interactiva
'use client';

import { useTransition } from 'react';
import { cancelarPedido } from '@/pedidos/acciones';

export function BotonCancelar({ pedidoId }: { pedidoId: string }) {
  const [pendiente, iniciar] = useTransition();
  return (
    <button type="button" disabled={pendiente} onClick={() => iniciar(() => cancelarPedido(pedidoId))}>
      {pendiente ? 'Cancelando…' : 'Cancelar'}
    </button>
  );
}
```

```ts
// pedidos/acciones.ts
'use server';

import { revalidatePath } from 'next/cache';

export async function cancelarPedido(pedidoId: string) {
  const sesion = await exigirSesion();                 // una acción es un endpoint público: siempre autorizar
  await repositorio.cancelar(pedidoId, sesion.usuarioId);
  revalidatePath('/pedidos');
}
```

La lista se renderiza en el servidor sin enviar JavaScript; solo el botón es código de cliente.

## Errores comunes

- `'use client'` en la raíz "para que funcione": se pierde la ventaja principal.
- Obtener datos con `useEffect` en lo que podía ser un componente de servidor.
- Importar código de servidor (acceso a base de datos, secretos) desde un componente de cliente.
- Tratar las acciones de servidor como funciones internas: son endpoints HTTP y necesitan autenticación, autorización y validación.
- Exponer secretos con el prefijo `NEXT_PUBLIC_`.
- Pasar a un componente de cliente propiedades que no se pueden serializar (funciones, clases).
- Asumir un comportamiento de caché sin verificarlo en la versión que usas.
- No definir `loading`, `error` y `not-found`.

## Taller

1. Crea una aplicación con tres rutas: inicio, lista y detalle con segmento dinámico.
2. Obtén los datos en componentes de servidor desde una API pública o un archivo.
3. Agrega `loading.tsx`, `error.tsx` y `not-found.tsx` y provoca cada caso.
4. Agrega un formulario que cree un registro con una acción de servidor y revalide la lista.
5. Abre las herramientas del navegador y comprueba cuánto JavaScript se envía. Marca toda la página con `'use client'` y compara.
6. Identifica en tu aplicación la frontera entre servidor y cliente y justifica dónde la pusiste.

Rama `taller/rx-n1-03-next`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué diferencia hay entre un componente de servidor y uno de cliente?
2. ¿Por qué colocar `'use client'` lo más abajo posible?
3. ¿Qué riesgo de seguridad tiene una acción de servidor?
4. ¿Qué problemas resuelve Next.js que React solo no resuelve?
5. ¿Cuándo no usarías Next.js?
