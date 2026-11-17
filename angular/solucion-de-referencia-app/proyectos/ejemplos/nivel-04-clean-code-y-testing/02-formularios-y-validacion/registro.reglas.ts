// registro.reglas.ts — puras: se prueban sin Angular
import { pattern, required, schema } from '@angular/forms/signals';

export interface Registro {
  correo: string;
  clave: string;
  confirmacion: string;
  tipoDocumento: 'cedula' | 'pasaporte';
  documento: string;
  contactos: Contacto[];
}
export interface Contacto { nombre: string; telefono: string; }

export const documentoValido = (tipo: Registro['tipoDocumento'], valor: string) => {
  const patron = tipo === 'cedula' ? /^\d{6,10}$/ : /^[A-Z0-9]{6,9}$/;
  return patron.test(valor)
    ? undefined
    : { kind: 'documento', message: tipo === 'cedula' ? 'Entre 6 y 10 dígitos' : 'Entre 6 y 9 letras o números' };
};

export const contactoEsquema = schema<Contacto>((contacto) => {
  required(contacto.nombre, { message: 'Escribe el nombre del contacto' });
  pattern(contacto.telefono, /^\d{10}$/, { message: 'El teléfono tiene 10 dígitos' });
});
