// 2. Un contrato con un método que "no está implementado" y su rediseño.

// Antes: todos los exportadores prometen tres formatos; el de Excel no sabe hacer PDF y lanza.
export interface ExportadorDeReportesAntes {
  aCsv(filas: string[][]): string;
  aPdf(filas: string[][]): Uint8Array;
  aExcel(filas: string[][]): Uint8Array;
}
export class ExportadorLigeroAntes implements ExportadorDeReportesAntes {
  aCsv(filas: string[][]) { return filas.map((f) => f.join(';')).join('\n'); }
  aPdf(): Uint8Array { throw new Error('No implementado'); }
  aExcel(): Uint8Array { throw new Error('No implementado'); }
}

// Después: un contrato por formato; cada exportador declara lo que sabe hacer y la pantalla ofrece solo eso.
export interface Formato { readonly extension: string; exportar(filas: string[][]): string | Uint8Array; }
export class Csv implements Formato {
  readonly extension = 'csv';
  exportar(filas: string[][]) { return filas.map((f) => f.map((c) => c.replace(/;/g, ',')).join(';')).join('\n'); }
}
export class Tsv implements Formato {
  readonly extension = 'tsv';
  exportar(filas: string[][]) { return filas.map((f) => f.join('\t')).join('\n'); }
}
/** La pantalla recibe la lista de formatos disponibles: no hay botón "PDF" que termine en una excepción. */
export const formatosOfrecidos = (formatos: readonly Formato[]) => formatos.map((f) => f.extension);
