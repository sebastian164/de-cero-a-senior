import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BotonAccion } from '../../compartido/ui/boton-accion';
import { CatalogoApi } from '../datos/catalogo.api';
import { ListaProductos } from '../ui/lista-productos';

@Component({
  imports: [ListaProductos, BotonAccion],
  template: `<h2>Catálogo</h2><app-lista-productos [productos]="productos()" /><app-boton-accion etiqueta="Actualizar" />`,
})
export class CatalogoPagina { protected readonly productos = toSignal(inject(CatalogoApi).listar(), { initialValue: [] }); }
