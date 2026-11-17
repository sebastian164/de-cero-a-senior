import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs';
import { RepositorioSolicitudes, SolicitudRechazada } from '../dominio/puertos';
import { Solicitud } from '../dominio/solicitud';

// Adaptador 1: el servidor.
@Injectable()
export class SolicitudesHttp extends RepositorioSolicitudes {
  private readonly http = inject(HttpClient);
  enviar(s: Solicitud): Observable<{ id: number }> {
    return this.http.post<{ id: number }>('/api/solicitudes', s).pipe(
      catchError((e: HttpErrorResponse) => throwError(() => (e.status === 422 ? new SolicitudRechazada(e.error?.campos ?? {}) : e))),
    );
  }
}

// Adaptador 2: en memoria, para desarrollar sin servidor.
@Injectable()
export class SolicitudesEnMemoria extends RepositorioSolicitudes {
  readonly guardadas: Solicitud[] = [];
  enviar(s: Solicitud): Observable<{ id: number }> {
    this.guardadas.push(s);
    return of({ id: -this.guardadas.length }).pipe(delay(0));     // ids negativos: se ve que no vino del servidor
  }
}
