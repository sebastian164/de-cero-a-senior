// Reglas puras del formulario de la clínica: se prueban sin Angular.
export type TipoDocumento = 'cedula' | 'pasaporte';
export interface Error1 { kind: string; message: string; }

export const documentoValido = (tipo: TipoDocumento, valor: string): Error1 | undefined => {
  const patron = tipo === 'cedula' ? /^\d{6,10}$/ : /^[A-Z0-9]{6,9}$/;
  return patron.test(valor) ? undefined
    : { kind: 'documento', message: tipo === 'cedula' ? 'Entre 6 y 10 dígitos' : 'Entre 6 y 9 letras o números' };
};

/** Edad cumplida en años. `fecha` en formato AAAA-MM-DD; `hoy` se inyecta para poder probar. */
export const edadEn = (fecha: string, hoy: Date): number | undefined => {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fecha);
  if (!m) return undefined;
  const [a, mes, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  let edad = hoy.getFullYear() - a;
  if (hoy.getMonth() + 1 < mes || (hoy.getMonth() + 1 === mes && hoy.getDate() < d)) edad--;
  return edad;
};
export const esMenorDeEdad = (fecha: string, hoy: Date) => { const e = edadEn(fecha, hoy); return e !== undefined && e < 18; };

export const fechaNacimientoValida = (fecha: string, hoy: Date): Error1 | undefined => {
  const e = edadEn(fecha, hoy);
  if (e === undefined) return { kind: 'fecha', message: 'Escribe tu fecha de nacimiento' };
  if (e < 0) return { kind: 'fecha', message: 'La fecha no puede ser futura' };
  return undefined;
};

export const clavesIguales = (clave: string, confirmacion: string): Error1 | undefined =>
  clave === confirmacion ? undefined : { kind: 'clavesDistintas', message: 'Las claves no coinciden' };

export const MIN_CONTACTOS = 1;
export const MAX_CONTACTOS = 3;
export const cantidadContactosValida = (n: number): Error1 | undefined =>
  n < MIN_CONTACTOS ? { kind: 'contactos', message: 'Agrega al menos un contacto' }
    : n > MAX_CONTACTOS ? { kind: 'contactos', message: 'Máximo tres contactos' } : undefined;
