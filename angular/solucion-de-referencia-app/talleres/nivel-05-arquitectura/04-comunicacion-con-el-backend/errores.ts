import { HttpErrorResponse } from '@angular/common/http';

export class PedidoNoEncontrado extends Error {}
export class SinConexion extends Error {}
export class TiempoAgotado extends Error {}
export class ErrorDeServidor extends Error { constructor(readonly estado: number) { super(`Error del servidor (${estado})`); } }
export class ErrorDeValidacion extends Error {
  constructor(readonly campos: Record<string, string>) { super('Datos inválidos'); }
}

/** Traduce lo que viene de la red a tipos del dominio: ningún componente interpreta códigos HTTP. */
export const aErrorDeDominio = (error: unknown): Error => {
  if (error instanceof Error && error.name === 'TimeoutError') return new TiempoAgotado();
  if (!(error instanceof HttpErrorResponse)) return error instanceof Error ? error : new Error(String(error));
  if (error.status === 0) return new SinConexion();
  if (error.status === 404) return new PedidoNoEncontrado();
  if (error.status === 422) return new ErrorDeValidacion(error.error?.campos ?? {});
  return new ErrorDeServidor(error.status);
};

/** Solo se reintenta lo que puede arreglarse solo: fallos de red y 5xx. Un 404 o un 422 no mejoran por insistir. */
export const esTransitorio = (error: unknown): boolean =>
  error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);

export const mensajeParaElUsuario = (error: unknown): string => {
  const e = aErrorDeDominio(error);
  if (e instanceof SinConexion) return 'Sin conexión. Revisa tu red e inténtalo de nuevo.';
  if (e instanceof TiempoAgotado) return 'El servidor tardó demasiado. Inténtalo de nuevo.';
  if (e instanceof PedidoNoEncontrado) return 'Ese pedido no existe.';
  if (e instanceof ErrorDeValidacion) return 'Revisa los datos marcados.';
  return 'No pudimos completar la operación. Inténtalo en unos minutos.';
};
