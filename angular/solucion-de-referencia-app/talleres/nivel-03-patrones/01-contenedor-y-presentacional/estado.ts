import { Injectable, signal } from '@angular/core';
import { Curso } from './ui';

@Injectable({ providedIn: 'root' })
export class Catalogo {
  readonly cursos = signal<readonly Curso[]>([
    { id: 'c1', nombre: 'Angular desde cero', categoria: 'Frontend', precio: 300000 },
    { id: 'c2', nombre: 'Pruebas con Vitest', categoria: 'Frontend', precio: 150000 },
    { id: 'c3', nombre: 'Kafka práctico', categoria: 'Backend', precio: 400000 },
  ]).asReadonly();
}
@Injectable({ providedIn: 'root' })
export class Inscripcion {
  private readonly ids = signal<readonly string[]>([]);
  readonly elegidos = this.ids.asReadonly();
  readonly puedeInscribirse = signal(true);
  readonly confirmada = signal(false);
  alternar(id: string) { this.ids.update((l) => (l.includes(id) ? l.filter((x) => x !== id) : [...l, id])); }
  confirmar() { this.confirmada.set(true); }
}
export const resumen = (cursos: readonly Curso[], ids: readonly string[]) => {
  const elegidos = cursos.filter((c) => ids.includes(c.id));
  return { cantidad: elegidos.length, total: elegidos.reduce((s, c) => s + c.precio, 0) };
};
