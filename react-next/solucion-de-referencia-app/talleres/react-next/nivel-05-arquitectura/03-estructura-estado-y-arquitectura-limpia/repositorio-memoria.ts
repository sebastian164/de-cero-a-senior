// Taller nivel-05/03, punto 4: el segundo adaptador del puerto RepositorioPedidos, en memoria.
import type { Pedido } from '@/funcionalidades/pedidos/dominio/pedido';
import type { RepositorioPedidos } from '@/funcionalidades/pedidos/dominio/puertos';

export function crearRepositorioMemoria(semilla: readonly Pedido[] = []): RepositorioPedidos & { todos(): readonly Pedido[] } {
  const filas = new Map(semilla.map((p) => [p.id, p]));
  return {
    async buscar(id) { return filas.get(id) ?? null; },
    async guardar(pedido) { filas.set(pedido.id, { ...pedido }); },
    todos: () => [...filas.values()],
  };
}
