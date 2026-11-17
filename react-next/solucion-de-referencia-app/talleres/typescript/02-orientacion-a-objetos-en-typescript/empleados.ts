// Taller TS 02 — punto 4: la jerarquía Empleado -> PorHoras / Asalariado reemplazada por composición.

// Antes (como referencia, para la prueba): herencia.
export abstract class EmpleadoHeredado {
  constructor(readonly nombre: string) {}
  abstract pagoMensual(): number;
}
export class EmpleadoPorHoras extends EmpleadoHeredado {
  constructor(nombre: string, private readonly horas: number, private readonly tarifa: number) { super(nombre); }
  pagoMensual() { return this.horas * this.tarifa; }
}
export class EmpleadoAsalariado extends EmpleadoHeredado {
  constructor(nombre: string, private readonly salario: number) { super(nombre); }
  pagoMensual() { return this.salario; }
}

// Después: el empleado tiene un esquema de pago, que se puede cambiar sin cambiar de clase.
export type EsquemaDePago =
  | { readonly tipo: 'porHoras'; readonly horas: number; readonly tarifa: number }
  | { readonly tipo: 'asalariado'; readonly salario: number }
  | { readonly tipo: 'comision'; readonly base: number; readonly ventas: number; readonly porcentaje: number };

export interface Empleado {
  readonly nombre: string;
  readonly pago: EsquemaDePago;
}

export function pagoMensual(pago: EsquemaDePago): number {
  switch (pago.tipo) {
    case 'porHoras':
      return pago.horas * pago.tarifa;
    case 'asalariado':
      return pago.salario;
    case 'comision':
      return pago.base + Math.round((pago.ventas * pago.porcentaje) / 100);
  }
}

/** Un empleado por horas pasa a asalariado: con herencia habría que crear otro objeto de otra clase. */
export const cambiarEsquema = (empleado: Empleado, pago: EsquemaDePago): Empleado => ({ ...empleado, pago });

export const nomina = (empleados: readonly Empleado[]) => empleados.reduce((t, e) => t + pagoMensual(e.pago), 0);
