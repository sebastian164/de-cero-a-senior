// ---- typescript/02-orientacion-a-objetos-en-typescript/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
interface Linea {
  readonly producto: string;
  readonly precio: number;
  readonly cantidad: number;
}

class Carrito {
  readonly #lineas: Linea[] = [];
  #pagado = false;

  agregar(producto: string, precio: number, cantidad: number): void {
    this.#exigirAbierto();
    if (cantidad <= 0) throw new Error('La cantidad debe ser mayor que cero');
    if (precio < 0) throw new Error('El precio no puede ser negativo');
    this.#lineas.push({ producto, precio, cantidad });
  }

  pagar(): void {
    this.#exigirAbierto();
    if (this.#lineas.length === 0) throw new Error('No se puede pagar un carrito vacío');
    this.#pagado = true;
  }

  get total(): number {                       // calculado: no puede desincronizarse
    return this.#lineas.reduce((suma, l) => suma + l.precio * l.cantidad, 0);
  }

  get lineas(): readonly Linea[] {
    return this.#lineas;                      // solo lectura para quien la recibe
  }

  #exigirAbierto(): void {
    if (this.#pagado) throw new Error('El carrito ya fue pagado');
  }
}

export { Carrito };
export type { Linea };
