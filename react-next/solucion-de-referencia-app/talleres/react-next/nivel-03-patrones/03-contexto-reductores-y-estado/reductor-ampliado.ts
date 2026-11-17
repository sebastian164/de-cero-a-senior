// Punto 2: cupones como acciones nuevas que envuelven al reductor del README, sin modificarlo.
import { reductorCarrito, totalCarrito, type AccionCarrito, type EstadoCarrito, type Linea } from '@/react-next/n3-03-carrito/reductor';

export type Cupon =
  | { codigo: string; tipo: 'porcentaje'; valor: number }
  | { codigo: string; tipo: 'fijo'; valor: number; minimo: number };

export const CUPONES: readonly Cupon[] = [
  { codigo: 'BIENVENIDA10', tipo: 'porcentaje', valor: 10 },
  { codigo: 'MENOS5000', tipo: 'fijo', valor: 5000, minimo: 30000 },
];

export interface EstadoAmpliado extends EstadoCarrito {
  readonly cupon: Cupon | null;
  readonly errorCupon: string | null;
}

export type AccionAmpliada =
  | AccionCarrito
  | { tipo: 'cuponAplicado'; codigo: string }
  | { tipo: 'cuponQuitado' }
  | { tipo: 'restaurado'; lineas: readonly Linea[] };

export const ESTADO_INICIAL: EstadoAmpliado = { lineas: [], cupon: null, errorCupon: null };

export function reductorAmpliado(estado: EstadoAmpliado, accion: AccionAmpliada): EstadoAmpliado {
  switch (accion.tipo) {
    case 'cuponAplicado': {
      const cupon = CUPONES.find((c) => c.codigo === accion.codigo.trim().toUpperCase());
      return cupon ? { ...estado, cupon, errorCupon: null } : { ...estado, errorCupon: 'Cupón no válido' };
    }
    case 'cuponQuitado':
      return { ...estado, cupon: null, errorCupon: null };
    case 'restaurado':
      return { ...estado, lineas: accion.lineas };
    default:
      return { ...estado, ...reductorCarrito(estado, accion) };
  }
}

export function descuentoCupon(estado: EstadoAmpliado): number {
  const subtotal = totalCarrito(estado);
  const c = estado.cupon;
  if (!c) return 0;
  if (c.tipo === 'porcentaje') return Math.round((subtotal * c.valor) / 100);
  return subtotal >= c.minimo ? Math.min(c.valor, subtotal) : 0;
}
