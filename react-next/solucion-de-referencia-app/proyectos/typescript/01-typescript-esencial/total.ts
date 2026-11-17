// ---- typescript/01-typescript-esencial/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
interface Item {
  readonly producto: string;
  readonly precio: number;
  readonly cantidad: number;
}

// Unión discriminada: cada caso tiene exactamente los datos que necesita
type Cupon =
  | { tipo: 'porcentaje'; porcentaje: number }
  | { tipo: 'fijo'; valor: number }
  | { tipo: 'envioGratis' };

function descuento(subtotal: number, cupon: Cupon): number {
  switch (cupon.tipo) {
    case 'porcentaje':
      return (subtotal * cupon.porcentaje) / 100;
    case 'fijo':
      return Math.min(cupon.valor, subtotal);
    case 'envioGratis':
      return 0;
    default: {
      const nunca: never = cupon;      // si se agrega un caso y se olvida aquí, no compila
      return nunca;
    }
  }
}

function total(items: readonly Item[], cupon?: Cupon): number {
  const subtotal = items.reduce((suma, item) => suma + item.precio * item.cantidad, 0);
  return cupon ? subtotal - descuento(subtotal, cupon) : subtotal;
}

export { total, descuento };
export type { Item, Cupon };
