export interface LineaPedido { readonly productoId: string; readonly cantidad: number; }
export interface Pedido { readonly id: string; readonly estado: 'pendiente' | 'pagado'; readonly lineas: readonly LineaPedido[]; }
