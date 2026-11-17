const coincide = (ruta, patron) => {
  const a = ruta.split('/'), b = patron.split('/');
  if (a.length !== b.length) return null;
  const params = {};
  for (let i = 0; i < a.length; i++) {
    if (b[i].startsWith(':')) params[b[i].slice(1)] = decodeURIComponent(a[i]);
    else if (a[i] !== b[i]) return null;
  }
  return params;
};
/** Tabla { 'GET /pedidos/:id': fn } → manejador. */
export const enrutador = (tabla) => (p) => {
  for (const [clave, fn] of Object.entries(tabla)) {
    const [metodo, patron] = clave.split(' ');
    if (metodo !== p.metodo) continue;
    const params = coincide(p.ruta, patron);
    if (params) return fn({ ...p, params });
  }
  return undefined;
};
/** Estado reiniciable de una sesión. */
export const estado = (inicial) => {
  const caja = { valor: structuredClone(inicial) };
  caja.reiniciar = () => { caja.valor = structuredClone(inicial); };
  return caja;
};
