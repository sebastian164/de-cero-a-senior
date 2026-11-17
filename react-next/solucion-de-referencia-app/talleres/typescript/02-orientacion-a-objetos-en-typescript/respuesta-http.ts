// Taller TS 02 — punto 5: una respuesta HTTP tipada como clase no tiene los métodos de la clase.
export class PedidoClase {
  constructor(readonly id: string, readonly lineas: readonly { precio: number; cantidad: number }[]) {}
  total(): number {
    return this.lineas.reduce((s, l) => s + l.precio * l.cantidad, 0);
  }
}

/** Lo que hace un cliente HTTP tipado: JSON.parse y una aserción. El compilador cree que es un PedidoClase. */
export function obtenerComoClase(cuerpo: string): PedidoClase {
  return JSON.parse(cuerpo) as PedidoClase;
}

/** Lo correcto: validar y construir la instancia (o, mejor, usar un tipo plano y funciones). */
export function obtenerYConstruir(cuerpo: string): PedidoClase {
  const datos: unknown = JSON.parse(cuerpo);
  if (typeof datos !== 'object' || datos === null || !('id' in datos) || !('lineas' in datos)) throw new Error('Respuesta inválida');
  const { id, lineas } = datos;
  if (typeof id !== 'string' || !Array.isArray(lineas)) throw new Error('Respuesta inválida');
  return new PedidoClase(id, lineas.map((l: unknown) => {
    if (typeof l !== 'object' || l === null || !('precio' in l) || !('cantidad' in l)) throw new Error('Línea inválida');
    const { precio, cantidad } = l;
    if (typeof precio !== 'number' || typeof cantidad !== 'number') throw new Error('Línea inválida');
    return { precio, cantidad };
  }));
}
