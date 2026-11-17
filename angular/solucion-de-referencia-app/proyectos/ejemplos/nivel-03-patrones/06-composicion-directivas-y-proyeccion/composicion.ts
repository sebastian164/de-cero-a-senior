import { NgTemplateOutlet } from '@angular/common';
import { contentChild, Directive, effect, inject, Injectable, input, Signal, signal, TemplateRef, ViewContainerRef, Component } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';

// Piezas que el README usa sin declarar: Permiso y Sesion.
export type Permiso = 'pedidos.cancelar' | 'pedidos.ver';
@Injectable({ providedIn: 'root' })
export class Sesion {
  readonly permisos = signal<readonly Permiso[]>(['pedidos.ver']);
  tienePermiso(p: Permiso): boolean { return this.permisos().includes(p); }
}

// Ejemplo 1: directiva como decorador
@Directive({ selector: '[appSiTienePermiso]' })
export class SiTienePermisoDirective {
  readonly permiso = input.required<Permiso>({ alias: 'appSiTienePermiso' });

  constructor() {
    const sesion = inject(Sesion);
    const plantilla = inject(TemplateRef);
    const contenedor = inject(ViewContainerRef);
    effect(() => {
      contenedor.clear();
      if (sesion.tienePermiso(this.permiso())) contenedor.createEmbeddedView(plantilla);
    });
  }
}

// Ejemplo 2: esqueleto con plantillas
@Component({
  selector: 'app-lista-con-estados',
  imports: [NgTemplateOutlet],
  template: `
    @if (cargando()) {
      <ng-container [ngTemplateOutlet]="plantillaCargando() ?? porDefectoCargando" />
    } @else if (elementos().length === 0) {
      <ng-content select="[vacio]" />
    } @else {
      <ul>
        @for (elemento of elementos(); track identificar()(elemento)) {
          <li><ng-container [ngTemplateOutlet]="plantillaElemento()" [ngTemplateOutletContext]="{ $implicit: elemento }" /></li>
        }
      </ul>
    }
    <ng-template #porDefectoCargando><p>Cargando…</p></ng-template>
  `,
})
export class ListaConEstadosComponent<T> {
  readonly elementos = input.required<readonly T[]>();
  readonly cargando = input(false);
  readonly identificar = input.required<(elemento: T) => unknown>();
  readonly plantillaElemento = contentChild.required<TemplateRef<{ $implicit: T }>>('elemento');
  readonly plantillaCargando = contentChild<TemplateRef<unknown>>('cargando');
}

// Ejemplo 3: lógica compartida con una función
/** Reutilizable en cualquier componente; debe llamarse en un contexto de inyección. */
export function usarParametroDeRuta(nombre: string): Signal<string | null> {
  const ruta = inject(ActivatedRoute);
  return toSignal(ruta.paramMap.pipe(map((p) => p.get(nombre))), { initialValue: null });
}

@Component({ template: `<h2>Pedido {{ id() }}</h2>` })   // plantilla añadida para verlo en pantalla
export class DetallePedidoComponent {
  protected readonly id = usarParametroDeRuta('id');
}
