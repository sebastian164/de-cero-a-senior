# 04 — Capa de datos y adaptadores

## El problema

Los componentes llaman a `fetch` directamente, usan la forma exacta de la respuesta del servidor y manejan a mano carga, error, caché y reintentos. Un cambio en la API se propaga por toda la interfaz, y cada pantalla resuelve los mismos problemas de forma distinta.

## El patrón

Tres piezas, de adentro hacia afuera:

| Pieza | Responsabilidad | Patrón |
|---|---|---|
| **Adaptador** | Traducir el formato de la API (DTO) al modelo de la aplicación, y validar lo que llega | Adapter |
| **Funciones de acceso** | Una función por operación: `listarPedidos`, `cancelarPedido`. Conocen la URL y los errores | Repository |
| **Fachada de consumo** | Cómo las usan los componentes: `await` en el servidor; hooks en el cliente | Facade |

Los componentes nunca conocen URLs ni DTO.

## Ejemplo

```ts
// pedidos/datos/esquema.ts — validar en la frontera
import { z } from 'zod';

const PedidoDto = z.object({
  id: z.number(),
  fecha_creacion: z.string(),
  cliente: z.object({ nombre_completo: z.string() }),
  estado: z.union([z.literal(1), z.literal(2), z.literal(3)]),
  valor_centavos: z.number(),
});

const ESTADOS = { 1: 'pendiente', 2: 'pagado', 3: 'cancelado' } as const;

// Adaptador
export const aPedido = (dto: z.infer<typeof PedidoDto>): Pedido => ({
  id: String(dto.id),
  creadoEn: new Date(dto.fecha_creacion),
  cliente: dto.cliente.nombre_completo,
  estado: ESTADOS[dto.estado],
  total: dto.valor_centavos / 100,
});

// pedidos/datos/pedidos.ts — funciones de acceso
export async function listarPedidos(filtro: FiltroPedidos, signal?: AbortSignal): Promise<Pedido[]> {
  const respuesta = await fetch(`${API}/pedidos?${new URLSearchParams(filtro)}`, { signal });
  if (!respuesta.ok) throw aErrorDeDominio(respuesta.status);
  return z.array(PedidoDto).parse(await respuesta.json()).map(aPedido);
}
```

Los tipos de TypeScript no validan en ejecución. Un esquema (Zod, Valibot) comprueba que la respuesta tiene la forma esperada y falla de forma clara si el servidor cambió.

### Consumo en el servidor (Next.js)

```tsx
export default async function PaginaPedidos({ searchParams }: { searchParams: Promise<FiltroPedidos> }) {
  const pedidos = await listarPedidos(await searchParams);
  return <ListaPedidos pedidos={pedidos} />;
}
```

### Consumo en el cliente: estado de servidor

Los datos que viven en el servidor no son estado de tu aplicación: son una **caché**. Una biblioteca de estado de servidor (TanStack Query, SWR) resuelve lo que a mano siempre sale mal:

| Problema | Lo resuelve con |
|---|---|
| Dos componentes piden lo mismo | Deduplicación por clave |
| Datos desactualizados | Revalidación al enfocar la ventana, por tiempo o por invalidación |
| Carga y error | Estados incluidos |
| Carreras y cancelación | Clave de consulta y `AbortSignal` |
| Fallos transitorios | Reintentos con retroceso |
| Tras una mutación | Invalidar las consultas afectadas |
| Interfaz instantánea | Actualizaciones optimistas con reversa |

```tsx
'use client';

export function usePedidos(filtro: FiltroPedidos) {
  return useQuery({
    queryKey: ['pedidos', filtro],
    queryFn: ({ signal }) => listarPedidos(filtro, signal),
    staleTime: 30_000,
  });
}

export function useCancelarPedido() {
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: cancelarPedido,
    onMutate: async (id) => {                                   // optimista
      await cliente.cancelQueries({ queryKey: ['pedidos'] });
      const anterior = cliente.getQueriesData<Pedido[]>({ queryKey: ['pedidos'] });
      cliente.setQueriesData<Pedido[]>({ queryKey: ['pedidos'] }, (lista) => lista?.filter((p) => p.id !== id));
      return { anterior };
    },
    onError: (_error, _id, contexto) =>                          // reversa
      contexto?.anterior.forEach(([clave, datos]) => cliente.setQueryData(clave, datos)),
    onSettled: () => cliente.invalidateQueries({ queryKey: ['pedidos'] }),
  });
}
```

### Errores con significado

```ts
export class SinConexion extends Error {}
export class NoEncontrado extends Error {}
export class SinPermiso extends Error {}

export const aErrorDeDominio = (estado: number): Error =>
  estado === 404 ? new NoEncontrado() : estado === 403 ? new SinPermiso() : new Error(`Error ${estado}`);
```

Los componentes deciden qué mostrar según el tipo de error, no según un código HTTP.

## Cuándo no

- Si la API ya entrega tu modelo y la mantiene tu mismo equipo, el adaptador puede ser trivial o esperar.
- Si todos los datos se obtienen en componentes de servidor y no hay interactividad con datos, la biblioteca de cliente sobra.
- No valides con esquema respuestas enormes en rutas críticas sin medir el costo.

## Errores comunes

- `fetch` y URLs en los componentes.
- `as Pedido` sobre una respuesta sin validar.
- DTO usados como modelo en toda la aplicación.
- Datos del servidor copiados a `useState` o a un almacén global.
- Claves de consulta inconsistentes: la invalidación no encuentra lo que debe.
- Mutaciones que no invalidan: la pantalla muestra datos viejos.
- Actualizaciones optimistas sin reversa.
- Obtener en el cliente lo que podía resolverse en el servidor.

## Taller

1. Escribe el esquema, el adaptador y las funciones de acceso de dos entidades de una API pública.
2. Cambia un campo en el DTO y cuenta los archivos que tocaste.
3. Haz que la API simulada devuelva un dato mal formado y comprueba que el error es claro.
4. Implementa una lista con consulta, mutación optimista y reversa ante fallo.
5. Abre la misma consulta desde dos componentes y verifica en la pestaña de red que solo sale una petición.
6. Decide para cada pantalla de tu aplicación si los datos se obtienen en servidor o en cliente y justifica.

Rama `taller/rx-n3-04-datos`.

## Preguntas senior

1. ¿Por qué validar en ejecución lo que ya está tipado?
2. ¿Qué diferencia hay entre estado de cliente y estado de servidor?
3. ¿Qué problemas resuelve una biblioteca de estado de servidor?
4. ¿Cómo implementas una actualización optimista de forma segura?
