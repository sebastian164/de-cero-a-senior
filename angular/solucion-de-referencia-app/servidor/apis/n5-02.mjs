import { enrutador, estado } from '../util.mjs';
const e = estado({
  productos: [{ id: 1, nombre: 'Teclado' }, { id: 2, nombre: 'Mouse' }],
  lecturas: 0,
  pedidos: Array.from({ length: 25 }, (_, i) => ({ id: `p${i + 1}`, estado: i % 3 === 0 ? 'pendiente' : i % 3 === 1 ? 'pagado' : 'enviado', cliente: `Cliente ${i + 1}` })),
});
export const reiniciar = e.reiniciar;
export const manejador = enrutador({
  'GET /productos': () => { e.valor.lecturas++; return { cuerpo: e.valor.productos, demora: 150 }; },
  'POST /productos': ({ cuerpo }) => { e.valor.productos.push({ id: e.valor.productos.length + 1, nombre: cuerpo.nombre }); return { estado: 201, cuerpo: {}, demora: 100 }; },
  'GET /lecturas': () => ({ cuerpo: { lecturas: e.valor.lecturas } }),
  'GET /pedidos': ({ url }) => {
    const est = url.searchParams.get('estado') ?? 'todos', pagina = Number(url.searchParams.get('pagina') ?? 1);
    const f = e.valor.pedidos.filter((p) => est === 'todos' || p.estado === est);
    return { cuerpo: { total: f.length, items: f.slice((pagina - 1) * 5, pagina * 5) }, demora: 100 };
  },
});
