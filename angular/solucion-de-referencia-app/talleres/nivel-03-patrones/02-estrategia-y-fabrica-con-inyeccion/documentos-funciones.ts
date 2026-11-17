// 3. Lo mismo con un Record de funciones: sin inyección, sin clases.
export type Pais = 'CO' | 'AR' | 'CL';
type Validar = (documento: string) => string | null;
export const VALIDAR_DOCUMENTO: Record<Pais, Validar> = {
  CO: (d) => (/^\d{6,10}$/.test(d) ? null : 'La cédula tiene entre 6 y 10 dígitos'),
  AR: (d) => (/^\d{7,8}$/.test(d.replace(/\./g, '')) ? null : 'El DNI tiene 7 u 8 dígitos'),
  CL: (d) => (/^\d{7,8}-[\dK]$/i.test(d.replace(/\./g, '')) ? null : 'El RUT tiene la forma 12345678-9'),
};
