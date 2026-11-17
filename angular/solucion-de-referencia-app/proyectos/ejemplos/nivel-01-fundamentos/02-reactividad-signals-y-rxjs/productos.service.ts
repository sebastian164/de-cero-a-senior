import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

// Piezas que el README usa sin declarar.
export interface Producto { readonly id: number; readonly nombre: string; }

@Injectable({ providedIn: 'root' })
export class ProductosService {
  private readonly http = inject(HttpClient);
  buscar(texto: string): Observable<Producto[]> {
    return this.http.get<Producto[]>('/api/productos', { params: { q: texto } });
  }
}
