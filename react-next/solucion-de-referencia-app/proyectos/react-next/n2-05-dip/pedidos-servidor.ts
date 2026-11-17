// ---- react-next/nivel-02-solid/05-dip/README.md (bloque 5), copiado tal cual entre el contexto agregado ----
import type { Pedido, RepositorioPedidos } from '../n2-03-lsp/repositorio';

// Contexto: una implementación del repositorio (el README la llama repositorioPostgres).
const repositorioPostgres: RepositorioPedidos & { cancelados: string[] } = {
  cancelados: [],
  listar: async (): Promise<readonly Pedido[]> => [],
};

// pedidos/casos-de-uso.ts
export const crearCancelarPedido = (repositorio: RepositorioPedidos, reloj: () => Date) =>
  async (pedidoId: string, usuarioId: string) => {
    if (!usuarioId) throw new Error('Sin usuario');
    if (reloj().getFullYear() < 2000) throw new Error('Reloj inválido');
    (repositorio as typeof repositorioPostgres).cancelados.push(pedidoId);
  };

// pedidos/composicion.ts — el único archivo que conoce las implementaciones
export const cancelarPedido = crearCancelarPedido(repositorioPostgres, () => new Date());

export { repositorioPostgres };
