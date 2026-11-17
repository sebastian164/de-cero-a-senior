import { enrutador } from '../util.mjs';

const FACTURAS = {
  'F-1': { id: 'F-1', cliente: 'Ana Gómez', fecha_emision: '2026-03-14T10:00:00Z', lineas: [{ descripcion: 'Teclado', valor: 100000 }, { descripcion: 'Mouse', valor: 40000 }] },
  // Forma del taller (factura del componente "antes" y del refactor).
  'F-2': {
    id: 'F-2', fecha_emision: '2026-03-14T10:00:00Z', cliente: { nombre_completo: 'Ferretería El Tornillo', tipo: 'mayorista' },
    lineas: [{ descripcion: 'Taladro', cantidad: 3, valor_unitario: 350000 }, { descripcion: 'Brocas', cantidad: 10, valor_unitario: 12000 }],
  },
};
export const manejador = enrutador({
  'GET /facturas/:id': ({ params }) => (FACTURAS[params.id] ? { cuerpo: FACTURAS[params.id] } : { estado: 404, cuerpo: {} }),
  'POST /correos': () => ({ estado: 202, cuerpo: { encolado: true } }),
});
