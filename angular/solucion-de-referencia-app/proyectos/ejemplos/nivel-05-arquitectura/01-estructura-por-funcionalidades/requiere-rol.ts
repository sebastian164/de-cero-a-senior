import { inject, Injectable, signal } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class SesionDemo { readonly rol = signal<string | null>(null); }
export const requiereRol = (rol: string): CanMatchFn => () => inject(SesionDemo).rol() === rol || inject(Router).parseUrl('/n5-01/ejemplo');
