// Contexto de nivel-06/02: `bd` con la forma de un cliente de base de datos (findUnique, findMany, delete), en memoria.
import 'server-only';

export interface FilaPedido {
  readonly id: string; readonly clienteId: string; readonly estado: string; readonly total: number;
  readonly lineas: readonly { producto: string; cantidad: number }[];
  // Campos que existen en la base y nunca deben salir: la proyección los deja fuera.
  readonly costoInterno: number; readonly notaDeRiesgo: string;
}
const semilla = (): FilaPedido[] => [
  { id: '0b7c6a2e-1d3f-4e5a-9b8c-7d6e5f4a3b21', clienteId: 'u-ana', estado: 'pagado', total: 32000, lineas: [{ producto: 'Café', cantidad: 1 }], costoInterno: 14100, notaDeRiesgo: 'ninguna' },
  { id: '1c8d7b3f-2e4a-4f6b-8c9d-8e7f6a5b4c32', clienteId: 'u-ana', estado: 'pendiente', total: 9000, lineas: [{ producto: 'Chocolate', cantidad: 1 }], costoInterno: 3900, notaDeRiesgo: 'ninguna' },
  { id: '2d9e8c4a-3f5b-4a7c-9d0e-9f8a7b6c5d43', clienteId: 'u-luis', estado: 'pagado', total: 64000, lineas: [{ producto: 'Prensa', cantidad: 1 }], costoInterno: 30000, notaDeRiesgo: 'contracargo previo' },
];
const global_ = globalThis as unknown as { filasPedidos?: FilaPedido[] };
const filas = () => (global_.filasPedidos ??= semilla());

export const bd = {
  pedido: {
    async findUnique({ where }: { where: { id: string } }): Promise<FilaPedido | null> {
      return filas().find((f) => f.id === where.id) ?? null;
    },
    async findMany({ where }: { where: { clienteId?: string } } = { where: {} }): Promise<FilaPedido[]> {
      return filas().filter((f) => !where.clienteId || f.clienteId === where.clienteId);
    },
    async delete({ where }: { where: { id: string } }): Promise<FilaPedido> {
      const i = filas().findIndex((f) => f.id === where.id);
      if (i === -1) throw new Error('Registro no encontrado');
      return filas().splice(i, 1)[0]!;
    },
  },
  reiniciar() { global_.filasPedidos = semilla(); },
};
