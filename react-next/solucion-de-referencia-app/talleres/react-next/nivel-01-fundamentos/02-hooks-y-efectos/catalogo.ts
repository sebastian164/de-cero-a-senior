// Catálogo que sirve /api/n1-02/productos (la ruta real) y el simulacro de red de las pruebas.
export const CATALOGO = [
  { id: '1', nombre: 'Café de Huila', categoria: 'bebidas' },
  { id: '2', nombre: 'Café descafeinado', categoria: 'bebidas' },
  { id: '3', nombre: 'Cacao en polvo', categoria: 'bebidas' },
  { id: '4', nombre: 'Pan de bono', categoria: 'panaderia' },
  { id: '5', nombre: 'Pandebono congelado', categoria: 'panaderia' },
] as const;

export const buscarEnCatalogo = (q: string, c: string) =>
  CATALOGO.filter((p) => (c === 'todas' || p.categoria === c) && p.nombre.toLowerCase().includes(q.toLowerCase()))
    .map(({ id, nombre }) => ({ id, nombre }));

/** Los términos cortos tardan más (devuelven más resultados): así una respuesta vieja llega después. */
export const retardo = (q: string) => Math.max(20, 250 - q.length * 60);
