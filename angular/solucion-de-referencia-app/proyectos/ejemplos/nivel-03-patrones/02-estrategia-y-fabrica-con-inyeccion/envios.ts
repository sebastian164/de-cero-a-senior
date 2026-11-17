import { inject, Injectable, InjectionToken, Provider } from '@angular/core';

export interface CalculoEnvio {
  readonly modalidad: string;
  calcular(pesoKg: number): number;
}

export const CALCULOS_ENVIO = new InjectionToken<CalculoEnvio[]>('cálculos de envío');

@Injectable() export class EnvioEstandar implements CalculoEnvio {
  readonly modalidad = 'estandar';
  calcular(pesoKg: number) { return 8000 + pesoKg * 500; }
}

@Injectable() export class EnvioExpress implements CalculoEnvio {
  readonly modalidad = 'express';
  calcular(pesoKg: number) { return 15000 + pesoKg * 900; }
}

// Función de configuración: el punto único donde se registran
export function provideEnvios(): Provider[] {
  return [
    { provide: CALCULOS_ENVIO, useClass: EnvioEstandar, multi: true },
    { provide: CALCULOS_ENVIO, useClass: EnvioExpress, multi: true },
  ];
}

// El contexto no conoce las estrategias concretas
@Injectable({ providedIn: 'root' })
export class CotizadorEnvio {
  private readonly calculos = new Map(inject(CALCULOS_ENVIO).map((c) => [c.modalidad, c]));

  cotizar(modalidad: string, pesoKg: number): number {
    const calculo = this.calculos.get(modalidad);
    if (!calculo) throw new Error(`Modalidad no soportada: ${modalidad}`);
    return calculo.calcular(pesoKg);
  }
}
