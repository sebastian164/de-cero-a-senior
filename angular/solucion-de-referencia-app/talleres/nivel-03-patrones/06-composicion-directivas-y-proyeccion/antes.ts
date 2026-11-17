import { Component, Directive, HostBinding, HostListener, inject, Injectable, OnDestroy, OnInit, signal } from '@angular/core';
import { Observable, of, Subject, delay, takeUntil } from 'rxjs';

/** 4. ANTES: un BaseComponent inventado del que heredan tres componentes. Se conserva para comparar. */
@Injectable({ providedIn: 'root' })
export class Avisos {
  readonly mensajes = signal<string[]>([]);
  mostrar(m: string) { this.mensajes.update((ms) => [...ms, m]); }
}

@Injectable({ providedIn: 'root' })
export class Datos {
  clientes(): Observable<string[]> { return of(['Ana', 'Luis']).pipe(delay(100)); }
  productos(): Observable<string[]> { return of(['Teclado']).pipe(delay(100)); }
}

@Directive()
export abstract class BaseComponent implements OnInit, OnDestroy {
  protected readonly destruido$ = new Subject<void>();
  protected readonly avisos = inject(Avisos);
  protected cargando = false;
  @HostBinding('class.resaltado') protected encima = false;
  @HostListener('mouseenter') entrar() { this.encima = true; }
  @HostListener('mouseleave') salir() { this.encima = false; }

  ngOnInit(): void { this.alIniciar(); }            // los hijos no pueden tener su propio ngOnInit sin llamar a super
  ngOnDestroy(): void { this.destruido$.next(); this.destruido$.complete(); }
  protected abstract alIniciar(): void;
  protected cargar<T>(fuente: Observable<T>, alTerminar: (v: T) => void) {
    this.cargando = true;
    fuente.pipe(takeUntil(this.destruido$)).subscribe({
      next: (v) => { this.cargando = false; alTerminar(v); },
      error: () => { this.cargando = false; this.avisos.mostrar('Error al cargar'); },
    });
  }
}

@Component({ selector: 'app-clientes-antes', template: `@if (cargando) { … } @else { {{ clientes.join(', ') }} }` })
export class ClientesAntes extends BaseComponent {
  private readonly datos = inject(Datos);
  protected clientes: string[] = [];
  protected alIniciar() { this.cargar(this.datos.clientes(), (c) => (this.clientes = c)); }
}

@Component({ selector: 'app-productos-antes', template: `@if (cargando) { … } @else { {{ productos.join(', ') }} }` })
export class ProductosAntes extends BaseComponent {
  private readonly datos = inject(Datos);
  protected productos: string[] = [];
  protected alIniciar() { this.cargar(this.datos.productos(), (p) => (this.productos = p)); }
}

@Component({ selector: 'app-resumen-antes', template: `Resumen` })
export class ResumenAntes extends BaseComponent {
  protected alIniciar() { /* hereda carga, avisos y resaltado aunque solo quiere el resaltado */ }
}
