// ---- react-next/nivel-05-arquitectura/03-estructura-estado-y-arquitectura-limpia/README.md (bloque 5, desde '// pedidos/dominio/puertos.ts'), copiado tal cual entre el contexto agregado ----
import type { Pedido } from './pedido';

// pedidos/dominio/puertos.ts
export interface RepositorioPedidos {
  buscar(id: string): Promise<Pedido | null>;
  guardar(pedido: Pedido): Promise<void>;
}
