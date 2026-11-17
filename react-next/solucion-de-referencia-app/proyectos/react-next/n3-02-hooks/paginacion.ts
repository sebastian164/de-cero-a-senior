// ---- react-next/nivel-03-patrones/02-hooks-personalizados/README.md (bloque 4), copiado tal cual entre el contexto agregado ----
// paginacion.ts — pura, probable con una línea
export const paginasTotales = (total: number, porPagina: number) => Math.max(1, Math.ceil(total / porPagina));
export const rebanar = <T,>(lista: readonly T[], pagina: number, porPagina: number) =>
  lista.slice((pagina - 1) * porPagina, pagina * porPagina);
