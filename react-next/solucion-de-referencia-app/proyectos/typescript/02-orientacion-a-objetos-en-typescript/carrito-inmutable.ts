// ---- typescript/02-orientacion-a-objetos-en-typescript/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
import type { Linea } from './carrito-clase';

interface EstadoCarrito {
  readonly lineas: readonly Linea[];
  readonly pagado: boolean;
}

function agregar(estado: EstadoCarrito, linea: Linea): EstadoCarrito {
  if (estado.pagado) throw new Error('El carrito ya fue pagado');
  if (linea.cantidad <= 0) throw new Error('La cantidad debe ser mayor que cero');
  return { ...estado, lineas: [...estado.lineas, linea] };   // devuelve uno nuevo
}

const total = (estado: EstadoCarrito) =>
  estado.lineas.reduce((suma, l) => suma + l.precio * l.cantidad, 0);

export { agregar, total };
export type { EstadoCarrito };
