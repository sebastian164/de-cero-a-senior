// ---- react-next/nivel-03-patrones/04-capa-de-datos-y-adaptadores/README.md (bloque 4), copiado tal cual entre el contexto agregado ----
export class SinConexion extends Error {}
export class NoEncontrado extends Error {}
export class SinPermiso extends Error {}

export const aErrorDeDominio = (estado: number): Error =>
  estado === 404 ? new NoEncontrado() : estado === 403 ? new SinPermiso() : new Error(`Error ${estado}`);
