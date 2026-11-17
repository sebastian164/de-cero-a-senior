// Taller TS 02 — punto 2: `private` de TypeScript se salta en ejecución; `#privado` no.
export class CuentaConPrivate {
  private saldo = 100;
  consultar() {
    return this.saldo;
  }
}

export class CuentaConNumeral {
  #saldo = 100;
  consultar() {
    return this.#saldo;
  }
}

/** Accede al campo por nombre en ejecución, como lo haría cualquier código JavaScript. */
export function leerPorNombre(objeto: object, campo: string): unknown {
  return Reflect.get(objeto, campo);
}

export function escribirPorNombre(objeto: object, campo: string, valor: unknown): void {
  Reflect.set(objeto, campo, valor);
}
