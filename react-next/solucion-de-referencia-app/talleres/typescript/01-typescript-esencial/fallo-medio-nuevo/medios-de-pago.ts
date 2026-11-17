// Punto 2: se agrega 'cripto' a la unión y NO se atiende en el switch. Debe dejar de compilar.
export type MedioDePago =
  | { readonly tipo: 'tarjeta'; readonly cuotas: number }
  | { readonly tipo: 'transferencia'; readonly banco: string }
  | { readonly tipo: 'efectivo'; readonly puntoDePago: string }
  | { readonly tipo: 'billetera'; readonly celular: string }
  | { readonly tipo: 'cripto'; readonly red: 'bitcoin' | 'ethereum' };

export function comision(valor: number, medio: MedioDePago): number {
  switch (medio.tipo) {
    case 'tarjeta':
      return Math.round(valor * 0.029);
    case 'transferencia':
      return 3500;
    case 'efectivo':
      return 2000;
    case 'billetera':
      return Math.round(valor * 0.015);
    default: {
      const nunca: never = medio;
      return nunca;
    }
  }
}
