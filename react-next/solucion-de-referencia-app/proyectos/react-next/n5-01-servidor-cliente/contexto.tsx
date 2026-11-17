// Contexto que el ejemplo "Después" (nivel-05/01) da por supuesto: el producto, sus reseñas y las piezas de servidor.
import 'server-only';
import { marked } from 'marked';

export interface Producto {
  readonly id: string; readonly nombre: string; readonly precio: number; readonly descripcion: string; readonly imagenes: readonly string[];
  // Campos internos: existen en la base, no deben llegar al navegador.
  readonly costoProveedor: number; readonly notasInternas: string;
}
export interface Resena { readonly id: string; readonly autor: string; readonly texto: string; readonly estrellas: number }

export const PRODUCTOS: readonly Producto[] = [
  { id: '1', nombre: 'Café de Huila 500 g', precio: 32000, imagenes: ['/productos/cafe-1.svg', '/productos/cafe-2.svg'],
    descripcion: '## Notas\n\nPanela, **cítricos** y cuerpo medio.\n\n- Tostión media\n- Altura: 1.700 m',
    costoProveedor: 14100, notasInternas: 'Proveedor con retrasos en marzo' },
  { id: '2', nombre: 'Chocolate de mesa', precio: 9000, imagenes: ['/productos/choco-1.svg'],
    descripcion: 'Cacao **70 %** sin azúcar añadida.', costoProveedor: 3900, notasInternas: 'Margen negociado hasta junio' },
];
export const RESENAS: Record<string, readonly Resena[]> = {
  '1': [{ id: 'r1', autor: 'Ana', texto: 'Excelente aroma', estrellas: 5 }, { id: 'r2', autor: 'Luis', texto: 'Muy bueno en prensa francesa', estrellas: 4 }],
  '2': [{ id: 'r3', autor: 'Eva', texto: 'Ideal con queso', estrellas: 5 }],
};
export const esperar = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Servidor: la biblioteca de Markdown se ejecuta aquí y no viaja al navegador. */
export async function Descripcion({ markdown }: { markdown: string }) {
  const html = await marked.parse(markdown);   // contenido propio, no de usuarios: no hace falta sanear
  return <div className="descripcion" dangerouslySetInnerHTML={{ __html: html }} />;
}

/** Servidor: sección lenta (700 ms) que se transmite cuando está lista. */
export async function Resenas({ productoId }: { productoId: string }) {
  await esperar(700);
  const resenas = RESENAS[productoId] ?? [];
  return (
    <section aria-labelledby="titulo-resenas">
      <h2 id="titulo-resenas">Reseñas</h2>
      <ul>{resenas.map((r) => <li key={r.id}>{'★'.repeat(r.estrellas)} {r.texto} — {r.autor}</li>)}</ul>
    </section>
  );
}

export const EsqueletoResenas = () => <div className="esqueleto" style={{ height: 96 }} aria-hidden="true" />;
