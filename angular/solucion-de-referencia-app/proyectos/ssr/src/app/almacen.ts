import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';

/**
 * Punto 4: ningún `localStorage` directo. En el navegador usa el almacenamiento real;
 * en el servidor, una memoria PROPIA DE CADA PETICIÓN (el servicio no es singleton del proceso: se provee en la raíz
 * de la aplicación, y el servidor crea una aplicación por petición).
 */
@Injectable({ providedIn: 'root' })
export class AlmacenLocal {
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly memoria = new Map<string, string>();
  leer(clave: string): string | null { return this.esNavegador ? localStorage.getItem(clave) : (this.memoria.get(clave) ?? null); }
  escribir(clave: string, valor: string): void { if (this.esNavegador) localStorage.setItem(clave, valor); else this.memoria.set(clave, valor); }
}
