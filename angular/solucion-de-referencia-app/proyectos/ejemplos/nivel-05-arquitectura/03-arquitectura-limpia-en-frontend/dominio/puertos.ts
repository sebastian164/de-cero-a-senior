// dominio/puertos.ts — lo que la aplicación necesita del exterior
import { Observable } from 'rxjs';
import { Pedido } from './pedido';

export abstract class RepositorioPedidos {
  abstract listar(): Observable<Pedido[]>;
  abstract cancelar(id: string): Observable<void>;
}
