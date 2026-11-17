// Almacén de pacientes en memoria (servidor).
import 'server-only';
import type { Paciente } from './esquema';

const pacientes = new Map<string, Paciente>();
pacientes.set('1012345678', {
  nombre: 'Paciente existente', documento: '1012345678', nacimiento: new Date('1990-01-01'), correo: 'existe@correo.example',
  telefono: '3001112233', contactos: [{ nombre: 'Contacto', telefono: '3009998877', parentesco: 'madre' }], aceptaDatos: true,
});

export async function documentoRegistrado(documento: string) {
  return pacientes.has(documento);
}

export async function guardarPaciente(p: Paciente) {
  pacientes.set(p.documento, p);
}

export const cantidadPacientes = () => pacientes.size;
