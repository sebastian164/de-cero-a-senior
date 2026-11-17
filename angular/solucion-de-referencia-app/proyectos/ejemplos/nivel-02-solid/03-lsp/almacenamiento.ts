import { Injectable } from '@angular/core';

export abstract class Lector {
  abstract leer(clave: string): string | null;
}

export abstract class Almacenamiento extends Lector {
  abstract guardar(clave: string, valor: string): void;
  abstract eliminar(clave: string): void;
}

// Los cuerpos "/* ... */" del README, escritos para poder ejecutarlos.
@Injectable() export class AlmacenamientoLocal extends Almacenamiento {
  guardar(clave: string, valor: string) { localStorage.setItem(clave, valor); }
  leer(clave: string) { return localStorage.getItem(clave); }
  eliminar(clave: string) { localStorage.removeItem(clave); }
}
const CONFIGURACION: Record<string, string> = { tema: 'claro' };
@Injectable() export class ConfiguracionFija extends Lector {
  leer(clave: string) { return CONFIGURACION[clave] ?? null; }
}

// Quien solo necesita leer inyecta Lector; quien necesita escribir inyecta Almacenamiento.
// Pedir escritura a ConfiguracionFija ya no compila.

export class AlmacenamientoEnMemoria extends Almacenamiento {
  private readonly datos = new Map<string, string>();
  guardar(clave: string, valor: string) { this.datos.set(clave, valor); }
  leer(clave: string) { return this.datos.get(clave) ?? null; }   // null si no existe, igual que el real
  eliminar(clave: string) { this.datos.delete(clave); }
}
