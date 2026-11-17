// ---- react-next/nivel-02-solid/03-lsp/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
export interface Pedido { readonly id: string; readonly cliente: string; readonly total: number }

export interface RepositorioPedidos {
  /** Devuelve [] si no hay pedidos. Lanza ErrorDeRed si no se puede consultar. Nunca devuelve null. */
  listar(): Promise<readonly Pedido[]>;
}

// La implementación real, la de memoria y el simulacro de pruebas cumplen lo mismo:
export const pedidosEnMemoria = (iniciales: Pedido[] = []): RepositorioPedidos => ({
  listar: async () => [...iniciales],
});
