import { Component, inject, Signal, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DestroyRef } from '@angular/core';
import { Observable } from 'rxjs';
import { Avisos, Datos } from './antes';
import { ResaltableDirective } from './directivas';

/**
 * 4. DESPUÉS: cada pieza de la clase base es ahora algo que se elige.
 *    - carga con estado y limpieza → la función usarCarga() con inject(), sin ngOnInit ni destruido$.
 *    - avisos → se inyectan donde hacen falta (dentro de usarCarga).
 *    - resaltar al pasar el mouse → ResaltableDirective aplicada con hostDirectives.
 */
export interface Carga<T> { readonly valor: Signal<T | undefined>; readonly cargando: Signal<boolean>; }

/** Debe llamarse en un contexto de inyección (inicializador de campo o constructor). */
export function usarCarga<T>(fuente: Observable<T>): Carga<T> {
  const avisos = inject(Avisos);
  const destruir = inject(DestroyRef);
  const valor = signal<T | undefined>(undefined);
  const cargando = signal(true);
  fuente.pipe(takeUntilDestroyed(destruir)).subscribe({
    next: (v) => { valor.set(v); cargando.set(false); },
    error: () => { cargando.set(false); avisos.mostrar('Error al cargar'); },
  });
  return { valor: valor.asReadonly(), cargando: cargando.asReadonly() };
}

@Component({
  selector: 'app-clientes',
  hostDirectives: [ResaltableDirective],
  template: `@if (clientes.cargando()) { Cargando clientes… } @else { Clientes: {{ clientes.valor()?.join(', ') }} }`,
})
export class Clientes { protected readonly clientes = usarCarga(inject(Datos).clientes()); }

@Component({
  selector: 'app-productos',
  hostDirectives: [ResaltableDirective],
  template: `@if (productos.cargando()) { Cargando productos… } @else { Productos: {{ productos.valor()?.join(', ') }} }`,
})
export class Productos { protected readonly productos = usarCarga(inject(Datos).productos()); }

/** El resumen solo quería el resaltado: ahora solo toma eso. */
@Component({ selector: 'app-resumen', hostDirectives: [ResaltableDirective], template: `Resumen` })
export class Resumen {}
