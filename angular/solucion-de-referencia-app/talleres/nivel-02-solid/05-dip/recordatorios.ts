import { HttpClient } from '@angular/common/http';
import { inject, Injectable, InjectionToken } from '@angular/core';
import { firstValueFrom } from 'rxjs';

// 2. Contratos definidos por la lógica.
export const RELOJ = new InjectionToken<() => number>('reloj', { providedIn: 'root', factory: () => () => Date.now() });
export abstract class RegistroDeAvisos { abstract yaAvisada(citaId: string): boolean; abstract marcar(citaId: string): void; }
export abstract class Avisador { abstract avisar(texto: string): void; }
export abstract class Metricas { abstract recordatorioEnviado(citaId: string): void; }
export abstract class RecordatoriosRemotos { abstract registrar(citaId: string): Promise<void>; }

export type EstadoRecordatorio = 'avisada' | 'pendiente' | 'vencida';
const UN_DIA = 24 * 3600_000;

// Lógica pura de la decisión, aparte de los efectos.
export const decidir = (fechaCita: number, ahora: number, yaAvisada: boolean): EstadoRecordatorio | 'avisar' => {
  const faltan = fechaCita - ahora;
  if (faltan < 0) return 'vencida';
  return faltan < UN_DIA && !yaAvisada ? 'avisar' : 'pendiente';
};

@Injectable({ providedIn: 'root' })
export class Recordatorios {
  private readonly ahora = inject(RELOJ);
  private readonly registro = inject(RegistroDeAvisos);
  private readonly avisador = inject(Avisador);
  private readonly metricas = inject(Metricas);
  private readonly remotos = inject(RecordatoriosRemotos);

  async programar(citaId: string, fechaIso: string): Promise<EstadoRecordatorio> {
    const decision = decidir(new Date(fechaIso).getTime(), this.ahora(), this.registro.yaAvisada(citaId));
    if (decision !== 'avisar') return decision;
    this.avisador.avisar('Tu cita es mañana');
    this.registro.marcar(citaId);
    this.metricas.recordatorioEnviado(citaId);
    await this.remotos.registrar(citaId);
    return 'avisada';
  }
}

// Detalles del navegador y de terceros.
@Injectable() export class RegistroEnLocalStorage extends RegistroDeAvisos {
  private leer(): string[] { return JSON.parse(localStorage.getItem('avisadas') ?? '[]'); }
  yaAvisada(id: string) { return this.leer().includes(id); }
  marcar(id: string) { localStorage.setItem('avisadas', JSON.stringify([...this.leer(), id])); }
}
@Injectable() export class RegistroEnMemoria extends RegistroDeAvisos {
  private readonly ids = new Set<string>();
  yaAvisada(id: string) { return this.ids.has(id); }
  marcar(id: string) { this.ids.add(id); }
}
@Injectable() export class AvisadorEnPagina extends Avisador {
  readonly avisos: string[] = [];
  avisar(texto: string) { this.avisos.push(texto); }
}
@Injectable() export class MetricasEnConsola extends Metricas {
  recordatorioEnviado(citaId: string) { console.info('métrica: recordatorio', citaId); }
}
@Injectable() export class RecordatoriosHttp extends RecordatoriosRemotos {
  private readonly http = inject(HttpClient);
  async registrar(citaId: string) { await firstValueFrom(this.http.post('/api/recordatorios', { citaId })); }
}

// 3. Dos ensamblajes: cambiar de localStorage a memoria es cambiar solo esta lista.
export const proveedoresNavegador = [
  { provide: RegistroDeAvisos, useClass: RegistroEnLocalStorage },
  { provide: Avisador, useClass: AvisadorEnPagina },
  { provide: Metricas, useClass: MetricasEnConsola },
  { provide: RecordatoriosRemotos, useClass: RecordatoriosHttp },
];
export const proveedoresEnMemoria = proveedoresNavegador.map((p) => (p.provide === RegistroDeAvisos ? { provide: RegistroDeAvisos, useClass: RegistroEnMemoria } : p));
