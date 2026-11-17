import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

// 1. "Mi servicio" antes: un servicio de recordatorios de citas con dependencias ocultas.
@Injectable({ providedIn: 'root' })
export class RecordatoriosAntes {
  private readonly http = inject(HttpClient);

  programar(citaId: string, fechaIso: string): string {
    const faltan = new Date(fechaIso).getTime() - Date.now();                       // reloj del sistema
    const yaAvisadas: string[] = JSON.parse(localStorage.getItem('avisadas') ?? '[]'); // almacenamiento del navegador
    if (faltan < 24 * 3600_000 && !yaAvisadas.includes(citaId)) {
      new Notification('Tu cita es mañana');                                        // API de notificaciones del navegador
      localStorage.setItem('avisadas', JSON.stringify([...yaAvisadas, citaId]));
      (window as any).mixpanel?.track('recordatorio', { citaId });                  // SDK de un tercero
      this.http.post('/api/recordatorios', { citaId }).subscribe();                  // backend (el detalle legítimo)
      return 'avisada';
    }
    return faltan < 0 ? 'vencida' : 'pendiente';
  }
}
