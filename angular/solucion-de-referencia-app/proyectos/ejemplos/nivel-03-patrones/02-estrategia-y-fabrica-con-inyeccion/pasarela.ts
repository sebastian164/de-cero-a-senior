import { inject, Injectable, InjectionToken } from '@angular/core';

// Piezas que el README usa sin declarar.
export interface PasarelaPagos { readonly nombre: string; }
export const CONFIGURACION = new InjectionToken<{ pais: string }>('configuración', { providedIn: 'root', factory: () => ({ pais: 'CO' }) });
@Injectable({ providedIn: 'root' }) export class PasarelaPse implements PasarelaPagos { readonly nombre = 'PSE'; }
@Injectable({ providedIn: 'root' }) export class PasarelaInternacional implements PasarelaPagos { readonly nombre = 'Internacional'; }

export const PASARELA = new InjectionToken<PasarelaPagos>('pasarela', {
  providedIn: 'root',
  factory: () => {
    const config = inject(CONFIGURACION);
    return config.pais === 'CO' ? inject(PasarelaPse) : inject(PasarelaInternacional);
  },
});

// Cuando la estrategia no tiene estado ni dependencias, una función basta:
export type Modalidad = 'estandar' | 'express' | 'recogida';
type CalculoEnvio = (pesoKg: number) => number;
export const CALCULOS: Record<Modalidad, CalculoEnvio> = {
  estandar: (kg) => 8000 + kg * 500,
  express: (kg) => 15000 + kg * 900,
  recogida: () => 0,
};
