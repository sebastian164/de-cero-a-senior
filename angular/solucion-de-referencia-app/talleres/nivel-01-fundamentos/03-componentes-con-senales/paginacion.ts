// Reglas puras de la paginación.
export const numeroDePaginas = (total: number, tamano: number) => Math.max(1, Math.ceil(total / Math.max(1, tamano)));
export const acotarPagina = (pagina: number, paginas: number) => Math.min(paginas, Math.max(1, Math.trunc(pagina) || 1));
