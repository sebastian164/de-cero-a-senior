import { Component, computed, input } from '@angular/core';
import { Usuario } from './modelo';

// 1. Tres componentes de presentación, antes (reciben Usuario) y después (reciben lo que usan).

@Component({ selector: 'app-avatar-antes', template: `<img [src]="usuario().perfil.fotoUrl" [alt]="usuario().nombres + ' ' + usuario().apellidos" width="32" height="32" />` })
export class AvatarAntes { readonly usuario = input.required<Usuario>(); }
@Component({ selector: 'app-avatar', template: `<img [src]="fotoUrl()" [alt]="nombre()" width="32" height="32" />` })
export class Avatar { readonly fotoUrl = input.required<string>(); readonly nombre = input.required<string>(); }

@Component({ selector: 'app-saludo-antes', template: `<p>Hola, {{ usuario().nombres }}. Último ingreso: {{ usuario().ultimoIngreso }}</p>` })
export class SaludoAntes { readonly usuario = input.required<Usuario>(); }
export type DatosSaludo = Pick<Usuario, 'nombres' | 'ultimoIngreso'>;
@Component({ selector: 'app-saludo', template: `<p>Hola, {{ datos().nombres }}. Último ingreso: {{ datos().ultimoIngreso }}</p>` })
export class Saludo { readonly datos = input.required<DatosSaludo>(); }

@Component({ selector: 'app-resumen-compras-antes', template: `<p>{{ usuario().pedidos.length }} pedidos por {{ total() }}</p>` })
export class ResumenComprasAntes {
  readonly usuario = input.required<Usuario>();
  protected readonly total = computed(() => this.usuario().pedidos.reduce((s, p) => s + p.total, 0));
}
@Component({ selector: 'app-resumen-compras', template: `<p>{{ totales().length }} pedidos por {{ total() }}</p>` })
export class ResumenCompras {
  readonly totales = input.required<readonly number[]>();
  protected readonly total = computed(() => this.totales().reduce((s, t) => s + t, 0));
}
