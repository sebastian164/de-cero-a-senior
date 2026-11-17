import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/**
 * Contrato, escrito como lo entiende quien lo usa:
 * - notificar(mensaje) se resuelve cuando el aviso quedó entregado a su destino.
 * - Un mensaje vacío o solo con espacios se rechaza con MensajeInvalido, en TODAS las implementaciones.
 * - Si el destino falla, la promesa se rechaza con NotificacionFallida (no se traga el error).
 */
export class MensajeInvalido extends Error {}
export class NotificacionFallida extends Error {}

export abstract class Notificador {
  abstract notificar(mensaje: string): Promise<void>;
  protected validar(mensaje: string): string {
    const limpio = mensaje.trim();
    if (!limpio) throw new MensajeInvalido('El mensaje está vacío');
    return limpio;
  }
}

/** En pantalla: los avisos quedan en una señal que pinta un componente. */
@Injectable({ providedIn: 'root' })
export class NotificadorEnPantalla extends Notificador {
  private readonly lista = signal<readonly string[]>([]);
  readonly avisos = this.lista.asReadonly();
  async notificar(mensaje: string): Promise<void> {
    const limpio = this.validar(mensaje);
    try { this.lista.update((l) => [...l, limpio]); } catch (e) { throw new NotificacionFallida(String(e)); }
  }
}

/** En consola: el destino es una función de registro inyectable (console.info por defecto). */
@Injectable({ providedIn: 'root' })
export class NotificadorEnConsola extends Notificador {
  registrar: (texto: string) => void = (t) => console.info(t);
  async notificar(mensaje: string): Promise<void> {
    const limpio = this.validar(mensaje);
    try { this.registrar(`[aviso] ${limpio}`); } catch (e) { throw new NotificacionFallida(String(e)); }
  }
}

/** A un servicio: POST /api/notificaciones. */
@Injectable({ providedIn: 'root' })
export class NotificadorRemoto extends Notificador {
  private readonly http = inject(HttpClient);
  async notificar(mensaje: string): Promise<void> {
    const limpio = this.validar(mensaje);
    try { await firstValueFrom(this.http.post('/api/notificaciones', { mensaje: limpio })); }
    catch { throw new NotificacionFallida('El servicio de notificaciones no respondió'); }
  }
}
