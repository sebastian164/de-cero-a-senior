import { Observable } from 'rxjs';
import { Solicitud } from './solicitud';

export class SolicitudRechazada extends Error {
  constructor(readonly campos: Readonly<Record<string, string>>) { super('El servidor rechazó la solicitud'); }
}

/** Puerto: lo que la aplicación necesita del exterior. */
export abstract class RepositorioSolicitudes {
  abstract enviar(solicitud: Solicitud): Observable<{ id: number }>;
}
