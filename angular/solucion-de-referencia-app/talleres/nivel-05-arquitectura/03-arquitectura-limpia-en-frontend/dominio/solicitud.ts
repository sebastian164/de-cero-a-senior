// Dominio de la solicitud de crédito: tipos y funciones puras. Sin Angular.
export type Paso = 'personales' | 'ingresos' | 'codeudor' | 'revision';
export type TipoEmpleo = 'empleado' | 'independiente';
export interface Codeudor { readonly nombre: string; readonly documento: string; }
export interface Solicitud {
  readonly nombre: string;
  readonly tipoEmpleo: TipoEmpleo;
  readonly ingresoMensual: number;
  readonly monto: number;
  readonly plazoMeses: number;
  readonly conCodeudor: boolean;
  readonly codeudor: Codeudor;
}
export type Errores = Readonly<Record<string, string>>;

export const MONTO_CON_CODEUDOR_OBLIGATORIO = 20_000_000;
export const TASA_MENSUAL = 0.015;
export const SOLICITUD_VACIA: Solicitud = { nombre: '', tipoEmpleo: 'empleado', ingresoMensual: 0, monto: 0, plazoMeses: 12, conCodeudor: false, codeudor: { nombre: '', documento: '' } };

export const requiereCodeudor = (s: Solicitud): boolean => s.conCodeudor || s.monto > MONTO_CON_CODEUDOR_OBLIGATORIO;

/** Los pasos aplicables dependen de la solicitud: el de codeudor solo existe si hace falta. */
export const pasosAplicables = (s: Solicitud): readonly Paso[] =>
  requiereCodeudor(s) ? ['personales', 'ingresos', 'codeudor', 'revision'] : ['personales', 'ingresos', 'revision'];

/** Sistema francés: cuota fija. */
export const cuotaMensual = (monto: number, plazoMeses: number, tasa = TASA_MENSUAL): number =>
  tasa === 0 ? monto / plazoMeses : (monto * tasa) / (1 - Math.pow(1 + tasa, -plazoMeses));

export const erroresDelPaso = (paso: Paso, s: Solicitud): Errores => {
  const e: Record<string, string> = {};
  if (paso === 'personales' && s.nombre.trim().length < 3) e['nombre'] = 'Escribe tu nombre completo';
  if (paso === 'ingresos') {
    if (!(s.ingresoMensual > 0)) e['ingresoMensual'] = 'Indica tus ingresos mensuales';
    if (!(s.monto > 0)) e['monto'] = 'Indica el monto que necesitas';
    // Regla de negocio del cliente: la cuota no puede pasar del 40 % del ingreso (el servidor la vuelve a validar).
    else if (s.ingresoMensual > 0 && cuotaMensual(s.monto, s.plazoMeses) > 0.4 * s.ingresoMensual) e['monto'] = 'La cuota supera el 40 % de tus ingresos';
    if (s.tipoEmpleo === 'independiente' && s.ingresoMensual > 0 && s.plazoMeses > 36) e['plazoMeses'] = 'Los independientes pueden pedir hasta 36 meses';
  }
  if (paso === 'codeudor' && requiereCodeudor(s)) {
    if (s.codeudor.nombre.trim().length < 3) e['codeudorNombre'] = 'Escribe el nombre del codeudor';
    if (!/^\d{6,10}$/.test(s.codeudor.documento)) e['codeudorDocumento'] = 'El documento tiene entre 6 y 10 dígitos';
  }
  return e;
};

/** A qué paso pertenece cada campo: lo usa la aplicación para llevar al usuario al paso que el servidor rechazó. */
export const PASO_DEL_CAMPO: Readonly<Record<string, Paso>> = {
  nombre: 'personales', tipoEmpleo: 'ingresos', ingresoMensual: 'ingresos', monto: 'ingresos', plazoMeses: 'ingresos',
  codeudorNombre: 'codeudor', codeudorDocumento: 'codeudor',
};
export const pasoDelCampo = (campo: string): Paso => PASO_DEL_CAMPO[campo] ?? 'revision';

export const puedeEnviar = (s: Solicitud): boolean => pasosAplicables(s).every((p) => Object.keys(erroresDelPaso(p, s)).length === 0);
