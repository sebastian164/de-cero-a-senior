// Taller TS 01 — puntos 1 y 2: unión discriminada de medios de pago y comisión con switch exhaustivo.
export type MedioDePago =
  | { readonly tipo: 'tarjeta'; readonly franquicia: 'visa' | 'mastercard' | 'amex'; readonly ultimos4: string; readonly cuotas: number }
  | { readonly tipo: 'transferencia'; readonly banco: string; readonly cuenta: string }
  | { readonly tipo: 'efectivo'; readonly puntoDePago: string }
  | { readonly tipo: 'billetera'; readonly proveedor: 'nequi' | 'daviplata'; readonly celular: string };

/** Comisión en pesos que cobra cada medio sobre un valor. */
export function comision(valor: number, medio: MedioDePago): number {
  switch (medio.tipo) {
    case 'tarjeta': {
      const base = medio.franquicia === 'amex' ? 0.035 : 0.029;
      const porCuotas = medio.cuotas > 1 ? 0.005 * (medio.cuotas - 1) : 0;
      return Math.round(valor * (base + porCuotas));
    }
    case 'transferencia':
      return 3500;
    case 'efectivo':
      return Math.max(2000, Math.round(valor * 0.01));
    case 'billetera':
      return Math.round(valor * 0.015);
    default: {
      const nunca: never = medio;
      return nunca;
    }
  }
}
