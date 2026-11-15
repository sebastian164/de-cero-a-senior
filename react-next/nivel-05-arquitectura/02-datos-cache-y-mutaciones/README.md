# 02 — Datos, caché y mutaciones en Next.js

El modelo de caché de Next.js ha cambiado de forma importante entre versiones mayores (qué se guarda por defecto, cómo se declara y con qué API se invalida). Esta sesión explica los conceptos, que son estables. **Antes de aplicar algo, confirma en la documentación de tu versión cuál es el comportamiento por defecto.**

## Idea

Una caché es una copia guardada para no repetir trabajo. Da velocidad a cambio de un riesgo: mostrar algo que ya no es cierto. Toda decisión de caché responde tres preguntas: qué se guarda, cuánto tiempo y cómo se entera de que cambió.

## Lo esencial

### Las capas donde algo puede quedar guardado

| Capa | Qué guarda | Vive |
|---|---|---|
| Memorización por petición | El resultado de una función llamada varias veces durante un mismo renderizado | Una petición |
| Caché de datos | El resultado de una consulta o de un cálculo | Entre peticiones, hasta que expire o se invalide |
| Caché de rutas | El HTML y la carga ya renderizados | Entre peticiones |
| Caché del enrutador en el navegador | Segmentos ya visitados | La sesión de navegación |
| CDN y navegador | Respuestas HTTP | Según cabeceras |

Cuando "no se actualiza", el primer paso es averiguar **en cuál** capa quedó el dato.

### Estático o dinámico

- **Estático:** se renderiza al construir o en segundo plano, y se sirve igual a todos. Muy rápido.
- **Dinámico:** se renderiza en cada petición. Necesario cuando depende de quién pide (cookies, cabeceras, sesión) o de datos que deben estar al instante.

Usar información de la petición (leer cookies, cabeceras o parámetros de búsqueda) hace dinámica esa parte. Las versiones recientes permiten mezclar en una misma página una cáscara estática con huecos dinámicos transmitidos.

### Estrategias de actualización

| Estrategia | Cuándo |
|---|---|
| Por tiempo | El dato tolera cierta antigüedad: catálogo, artículos |
| Bajo demanda, por etiqueta o por ruta | Se sabe exactamente cuándo cambió: tras una mutación o un *webhook* |
| Sin caché | Datos por usuario, o que deben ser exactos |

### Deduplicar dentro de una petición

Si el diseño, la página y un componente necesitan al usuario actual, que lo pidan los tres: envolver la función con `cache` de React hace que se ejecute una sola vez por petición. Evita pasar datos por propiedades solo para no repetir la consulta.

```ts
import 'server-only';
import { cache } from 'react';

export const obtenerUsuarioActual = cache(async () => {
  const sesion = await leerSesion();
  return sesion ? buscarUsuario(sesion.usuarioId) : null;
});
```

### Mutaciones con acciones de servidor

Una acción de servidor es una función asíncrona marcada con `'use server'`, invocable desde un formulario o desde un componente de cliente. Next.js la expone como un endpoint.

Pasos de toda acción:

1. **Autenticar:** ¿quién llama?
2. **Validar** la entrada con un esquema.
3. **Autorizar:** ¿puede hacer esto sobre este recurso?
4. **Ejecutar** la mutación.
5. **Invalidar** la caché afectada.
6. **Responder:** devolver un resultado o redirigir.

```ts
'use server';

export async function cancelarPedido(pedidoId: string): Promise<Resultado> {
  const usuario = await obtenerUsuarioActual();
  if (!usuario) return { ok: false, error: 'Debes iniciar sesión' };

  const id = z.string().uuid().safeParse(pedidoId);
  if (!id.success) return { ok: false, error: 'Pedido inválido' };

  const pedido = await buscarPedido(id.data);
  if (!pedido || pedido.clienteId !== usuario.id) return { ok: false, error: 'Pedido no encontrado' };
  if (!sePuedeCancelar(pedido, new Date())) return { ok: false, error: 'El pedido ya no se puede cancelar' };

  await marcarCancelado(pedido.id);
  revalidateTag(`pedidos-${usuario.id}`);
  return { ok: true };
}
```

Los errores esperados se **devuelven** como valores; las excepciones quedan para lo inesperado.

### Interfaz optimista

```tsx
'use client';

export function ListaPedidos({ pedidos }: { pedidos: readonly Pedido[] }) {
  const [visibles, quitarOptimista] = useOptimistic(pedidos, (actuales, id: string) =>
    actuales.filter((p) => p.id !== id));
  const [, iniciar] = useTransition();

  function cancelar(id: string) {
    iniciar(async () => {
      quitarOptimista(id);                              // se refleja de inmediato
      const resultado = await cancelarPedido(id);       // si falla, React vuelve al estado real
      if (!resultado.ok) avisar(resultado.error);
    });
  }
  /* … */
}
```

### Endpoints HTTP propios

Los manejadores de ruta (`route.ts`) son para lo que consumen terceros: *webhooks*, aplicaciones móviles, integraciones. Para alimentar tus propias páginas no hacen falta: el componente de servidor consulta directamente y las mutaciones van por acciones.

## Errores comunes

- Asumir el comportamiento de caché de otra versión o de un tutorial antiguo.
- Guardar en caché datos que dependen del usuario sin incluirlo en la clave: un usuario ve datos de otro.
- Mutaciones que no invalidan.
- Invalidar todo ante cualquier cambio.
- Acciones de servidor sin autenticación, validación ni autorización.
- Lanzar excepciones para errores de validación.
- Llamar a un endpoint propio desde un componente de servidor en lugar de a la función.
- Usar acciones de servidor para leer datos.
- Hacer dinámica una página entera por leer una cookie en el diseño.

## Taller

1. Construye una lista y un detalle con datos de un origen que puedas modificar. Para cada consulta decide su estrategia de actualización y justifícala.
2. Verifica cada decisión: modifica el dato en el origen y observa cuándo cambia la página en producción (`next build` y `next start`; el modo de desarrollo se comporta distinto).
3. Implementa una mutación con los seis pasos y la invalidación por etiqueta.
4. Agrega interfaz optimista y comprueba la reversa ante fallo.
5. Envuelve la consulta del usuario actual con `cache` y demuestra que se ejecuta una vez por petición.
6. Provoca una fuga entre usuarios con una clave de caché mal elegida y corrígela.
7. Documenta el comportamiento de caché por defecto de la versión que usas, con la referencia a su documentación.

Rama `taller/rx-n5-02-datos-cache`.

## Preguntas senior

1. ¿En qué capas puede quedar guardado un dato y cómo diagnosticas cuál es?
2. ¿Qué hace dinámica a una ruta?
3. ¿Cuándo invalidar por tiempo y cuándo bajo demanda?
4. ¿Qué debe hacer toda acción de servidor?
5. ¿Qué riesgo tiene guardar en caché datos por usuario?
