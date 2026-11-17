// Tipo que el README usa sin declarar.
export interface Pedido {
  readonly id: string;
  readonly cliente: string;
  readonly items: readonly { readonly precio: number; readonly cantidad: number }[];
}

// pedido.ts — regla pura, probable sin Angular
export const totalConIva = (pedido: Pedido): number =>
  pedido.items.reduce((suma, i) => suma + i.precio * i.cantidad, 0) * 1.19;
