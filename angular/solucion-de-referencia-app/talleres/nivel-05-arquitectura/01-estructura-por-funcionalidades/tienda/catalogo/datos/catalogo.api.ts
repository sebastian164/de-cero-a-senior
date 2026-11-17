import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Producto } from '../dominio/producto';

@Injectable({ providedIn: 'root' })
export class CatalogoApi {
  private readonly http = inject(HttpClient);
  listar(): Observable<Producto[]> { return this.http.get<Producto[]>('/api/catalogo'); }
}
