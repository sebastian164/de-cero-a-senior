// Punto 2: la implementación real del contrato del README (repositorio HTTP). Cumple lo mismo que la de memoria.
import { pedidosEnMemoria, type Pedido, type RepositorioPedidos } from '@/react-next/n2-03-lsp/repositorio';

export class ErrorDeRed extends Error {
  constructor(motivo: string) { super(`No se pudo consultar: ${motivo}`); this.name = 'ErrorDeRed'; }
}

export function pedidosHttp(base: string, fetchFn: typeof fetch = fetch): RepositorioPedidos {
  return {
    async listar() {
      let respuesta: Response;
      try {
        respuesta = await fetchFn(`${base}/pedidos`);
      } catch (e) {
        throw new ErrorDeRed(e instanceof Error ? e.message : 'desconocido');
      }
      if (respuesta.status === 204) return [];
      if (!respuesta.ok) throw new ErrorDeRed(`estado ${respuesta.status}`);
      const cuerpo: unknown = await respuesta.json();
      if (cuerpo === null) return []; // el servidor real responde null cuando no hay: el contrato dice []
      if (!Array.isArray(cuerpo)) throw new ErrorDeRed('respuesta inesperada');
      return cuerpo.map((p: { id: number; cliente: string; total: number }): Pedido => ({ id: String(p.id), cliente: p.cliente, total: p.total }));
    },
  };
}

/**
 * Variante de la implementación en memoria que también cumple la parte "lanza ErrorDeRed si no se puede consultar" del contrato.
 * Es parte de la implementación (no un objeto escrito en la prueba): la batería de contrato ejercita este código.
 */
export function pedidosEnMemoriaConFallo(semilla: Pedido[], opciones: { falla: boolean }): RepositorioPedidos {
  const base = pedidosEnMemoria(semilla);
  return {
    async listar() {
      if (opciones.falla) throw new ErrorDeRed('almacén en memoria no disponible');
      return base.listar();
    },
  };
}
