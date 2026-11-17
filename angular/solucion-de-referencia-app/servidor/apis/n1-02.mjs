import { enrutador } from '../util.mjs';

const PRODUCTOS = [
  { id: 1, nombre: 'Teclado', precio: 120000 }, { id: 2, nombre: 'Televisor', precio: 1500000 },
  { id: 3, nombre: 'Tetera', precio: 80000 }, { id: 4, nombre: 'Mouse', precio: 40000 }, { id: 5, nombre: 'Monitor', precio: 700000 },
];
// Latencia variable a propósito: las búsquedas cortas tardan más que las largas, para provocar la carrera.
export const manejador = enrutador({
  'GET /productos': ({ url }) => {
    const q = (url.searchParams.get('q') ?? '').toLowerCase();
    return { cuerpo: PRODUCTOS.filter((p) => p.nombre.toLowerCase().includes(q)), demora: q.length <= 2 ? 1200 : 150 };
  },
  'POST /carrito': () => ({ estado: 201, cuerpo: { ok: true }, demora: 800 }),
});
