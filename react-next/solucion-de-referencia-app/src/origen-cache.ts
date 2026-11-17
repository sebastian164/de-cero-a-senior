// Origen de datos del taller de nivel-05/02: un "sistema externo" que se puede modificar y que cuenta cuántas veces lo consultan.
import 'server-only';

export interface Articulo { readonly id: string; readonly nombre: string; readonly precio: number }
interface Estado { articulos: Map<string, Articulo>; lecturas: Record<string, number>; usuarioOculto: string | null }
const g = globalThis as unknown as { origenCache?: Estado };
const estado = (): Estado => (g.origenCache ??= {
  articulos: new Map([['1', { id: '1', nombre: 'Café', precio: 32000 }], ['2', { id: '2', nombre: 'Chocolate', precio: 9000 }]]),
  lecturas: {}, usuarioOculto: null,
});
const contar = (k: string) => { const e = estado(); e.lecturas[k] = (e.lecturas[k] ?? 0) + 1; };

export async function leerLista(): Promise<readonly Articulo[]> { contar('lista'); return [...estado().articulos.values()]; }
export async function leerDetalle(id: string): Promise<Articulo | null> { contar(`detalle-${id}`); return estado().articulos.get(id) ?? null; }
export function modificar(id: string, precio: number) {
  const e = estado(); const a = e.articulos.get(id);
  if (!a) throw new Error('no existe');
  e.articulos.set(id, { ...a, precio });
}
export const lecturas = () => ({ ...estado().lecturas });
export function reiniciar() { g.origenCache = undefined; }

/** Entrada oculta (no es argumento): lo que hace peligrosa una clave de caché mal elegida. */
export function fijarUsuarioOculto(id: string) { estado().usuarioOculto = id; }
export const usuarioOculto = () => estado().usuarioOculto;
