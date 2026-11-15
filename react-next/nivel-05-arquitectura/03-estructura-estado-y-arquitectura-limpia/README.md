# 03 — Estructura, estado y arquitectura limpia

Requisito: los niveles de arquitectura de la [ruta Java](../../../java/nivel-05-arquitectura/). Las ideas de organización por funcionalidades, clasificación del estado y capas están desarrolladas, de forma independiente del framework, en el [Nivel 5 de la ruta Angular](../../../angular/nivel-05-arquitectura/); léelas allí. Esta sesión las concreta en React y Next.js.

## Estructura por funcionalidades

La carpeta `app/` es el enrutador: debe contener rutas delgadas. El código de la aplicación vive fuera, organizado por funcionalidad.

```
src/
├── app/                          solo rutas: componen funcionalidades
│   ├── (publico)/                grupo de rutas con su propio diseño
│   ├── (privado)/pedidos/
│   │   ├── page.tsx
│   │   └── [id]/page.tsx
│   └── layout.tsx
├── funcionalidades/
│   └── pedidos/
│       ├── dominio/              tipos y reglas puras. Sin React ni Next
│       ├── datos/                consultas, adaptadores, esquemas (server-only)
│       ├── acciones/             acciones de servidor
│       ├── ui/                   componentes
│       └── index.ts              API pública de la funcionalidad
├── compartido/
│   ├── ui/                       sistema de diseño
│   └── lib/                      utilidades sin lógica de negocio
└── nucleo/                       sesión, configuración, registro
```

```tsx
// app/(privado)/pedidos/page.tsx — delgada
import { ListaDePedidos, listarPedidosDelUsuario } from '@/funcionalidades/pedidos';

export default async function Pagina({ searchParams }: { searchParams: Promise<{ estado?: string }> }) {
  const { estado } = await searchParams;
  return <ListaDePedidos pedidos={await listarPedidosDelUsuario({ estado })} />;
}
```

Reglas de dependencia, verificadas con reglas de límites del *linter*: una funcionalidad no importa el interior de otra; `compartido` no importa funcionalidades; `dominio` no importa React, Next ni la capa de datos. Las carpetas con guion bajo (`_componentes`) y los grupos entre paréntesis permiten organizar dentro de `app/` sin crear rutas.

## Estado: dónde vive cada cosa

| Tipo | En React y Next.js |
|---|---|
| De servidor | Componentes de servidor; en cliente, una biblioteca de consultas. **No** un almacén global |
| De URL | Segmentos y parámetros de búsqueda (`searchParams`, `useSearchParams`). Filtros, página, pestaña, orden |
| De formulario | El DOM, `useActionState` o una biblioteca de formularios |
| De interfaz local | `useState`, `useReducer` |
| Compartido de cliente | Contexto (cambia poco) o almacén externo con selectores (cambia mucho) |

Con los componentes de servidor, el estado global de cliente se reduce mucho: lo que antes era "traer datos y guardarlos en el almacén" pasa a ser "consultar en el servidor". Antes de agregar una biblioteca de estado, lista qué estado de cliente compartido queda de verdad.

**El estado en la URL** es la opción por defecto para todo lo que el usuario querría compartir o recuperar al recargar. Cambiar un filtro es navegar; el servidor vuelve a consultar y la página se actualiza.

```tsx
'use client';
export function FiltroDeEstado() {
  const ruta = usePathname();
  const parametros = useSearchParams();
  const router = useRouter();

  function cambiar(estado: string) {
    const nuevos = new URLSearchParams(parametros);
    nuevos.set('estado', estado);
    nuevos.delete('pagina');
    router.replace(`${ruta}?${nuevos}`);
  }
  /* … */
}
```

## Arquitectura limpia, en la dosis correcta

La regla es la del resto del curso: las dependencias apuntan al dominio.

```
ui ─► acciones / casos de uso ─► dominio ◄─ datos
```

```ts
// pedidos/dominio/pedido.ts — sin importaciones de framework
export const sePuedeCancelar = (pedido: Pedido, ahora: Date): boolean =>
  (pedido.estado === 'pendiente' || pedido.estado === 'pagado') && horasEntre(pedido.creadoEn, ahora) <= 24;

// pedidos/dominio/puertos.ts
export interface RepositorioPedidos {
  buscar(id: string): Promise<Pedido | null>;
  guardar(pedido: Pedido): Promise<void>;
}

// pedidos/aplicacion/cancelar-pedido.ts — caso de uso: recibe sus dependencias
export const crearCancelarPedido = (repositorio: RepositorioPedidos, reloj: () => Date) =>
  async (pedidoId: string, usuarioId: string): Promise<Resultado> => {
    const pedido = await repositorio.buscar(pedidoId);
    if (!pedido || pedido.clienteId !== usuarioId) return { ok: false, error: 'Pedido no encontrado' };
    if (!sePuedeCancelar(pedido, reloj())) return { ok: false, error: 'El pedido ya no se puede cancelar' };
    await repositorio.guardar({ ...pedido, estado: 'cancelado' });
    return { ok: true };
  };

// pedidos/composicion.ts — ensamblaje, solo servidor
import 'server-only';
export const cancelarPedidoCasoDeUso = crearCancelarPedido(repositorioPostgres, () => new Date());

// pedidos/acciones/cancelar.ts — adaptador de entrada: delgado
'use server';
export async function cancelarPedido(pedidoId: string) {
  const usuario = await exigirUsuario();
  const resultado = await cancelarPedidoCasoDeUso(pedidoId, usuario.id);
  if (resultado.ok) revalidateTag(`pedidos-${usuario.id}`);
  return resultado;
}
```

La acción de servidor es un adaptador, igual que un controlador REST: autentica, delega e invalida. El caso de uso se prueba sin Next.js ni base de datos.

**Con Next.js el frontend incluye código de backend.** Si la aplicación consulta su propia base de datos, las reglas de negocio viven en este proyecto y merecen esta estructura. Si solo consume una API de otro equipo (un BFF), el dominio propio es delgado y basta con adaptadores y funciones de acceso.

| Aplicación | Estructura razonable |
|---|---|
| Sitio de contenido o CRUD sobre una API | Funcionalidades con `datos` y `ui` |
| Reglas propias moderadas | Más `dominio` con funciones puras |
| Next.js como aplicación completa con su base de datos y reglas | Capas con puertos y composición |

## Errores comunes

- Toda la aplicación dentro de `app/`.
- Carpetas raíz por tipo (`components/`, `hooks/`, `utils/`).
- Biblioteca de estado global para guardar datos del servidor.
- Filtros y paginación en estado local.
- Acciones de servidor con toda la lógica de negocio adentro.
- Consultas a la base de datos dispersas por los componentes.
- Capas completas para un sitio que solo muestra contenido.
- Reglas duplicadas entre el cliente y el servidor que divergen.

## Taller

1. Reorganiza una aplicación por funcionalidades con rutas delgadas y configura la verificación de límites.
2. Clasifica todo su estado; mueve a la URL lo que corresponda y elimina el estado global que resulte innecesario.
3. Implementa un caso de uso con reglas reales en capas, con su prueba sin framework.
4. Agrega un segundo adaptador de datos (en memoria) y cámbialo en la composición.
5. Para una pantalla que solo lista datos, justifica por qué no le aplicarías las capas.

Rama `taller/rx-n5-03-arquitectura`.

## Preguntas senior

1. ¿Qué debe contener la carpeta `app/` y qué no?
2. ¿Cómo cambian los componentes de servidor la necesidad de estado global?
3. ¿Qué papel juega una acción de servidor en una arquitectura por capas?
4. ¿Cuándo una aplicación Next.js merece arquitectura de backend?
