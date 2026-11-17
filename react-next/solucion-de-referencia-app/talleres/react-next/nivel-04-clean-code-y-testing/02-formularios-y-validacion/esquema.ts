// Punto 1: el esquema del registro de la clínica, compartido por cliente y servidor.
import { z } from 'zod';

export const MAXIMO_CONTACTOS = 3;

export const EsquemaContacto = z.object({
  nombre: z.string().trim().min(3, 'Escribe el nombre del contacto'),
  telefono: z.string().regex(/^3\d{9}$/, 'Celular de 10 dígitos que empiece por 3'),
  parentesco: z.enum(['madre', 'padre', 'pareja', 'hijo', 'otro'], { message: 'Elige el parentesco' }),
});

// La fecha de hoy se lee al validar, no al cargar el módulo: con Cache Components leerla al cargar rompe el prerender
// (los formularios dejaban de salir en el HTML del servidor) y dejaría congelado el "hoy" del primer arranque.
const hoy = () => new Date();
const edadEn = (nacimiento: Date, fecha: Date) => {
  const edad = fecha.getFullYear() - nacimiento.getFullYear();
  const cumplio = fecha.getMonth() > nacimiento.getMonth() || (fecha.getMonth() === nacimiento.getMonth() && fecha.getDate() >= nacimiento.getDate());
  return cumplio ? edad : edad - 1;
};

export const EsquemaPaciente = z
  .object({
    nombre: z.string().trim().min(3, 'Escribe el nombre completo'),
    documento: z.string().regex(/^\d{6,10}$/, 'Entre 6 y 10 dígitos'),
    nacimiento: z.coerce.date({ message: 'Fecha inválida' }).refine((fecha) => fecha <= hoy(), 'La fecha no puede ser futura'),
    correo: z.string().email('Escribe un correo válido'),
    telefono: z.string().regex(/^3\d{9}$/, 'Celular de 10 dígitos que empiece por 3'),
    acudiente: z.string().trim().optional(),
    contactos: z.array(EsquemaContacto).min(1, 'Agrega al menos un contacto de emergencia').max(MAXIMO_CONTACTOS, `Máximo ${MAXIMO_CONTACTOS} contactos`),
    aceptaDatos: z.literal(true, { message: 'Debes autorizar el tratamiento de datos' }),
  })
  // Validaciones entre campos
  .refine((d) => edadEn(d.nacimiento, hoy()) >= 18 || (d.acudiente ?? '').length >= 3, {
    path: ['acudiente'], message: 'Los menores de edad necesitan un acudiente',
  })
  .refine((d) => d.contactos.every((c) => c.telefono !== d.telefono), {
    path: ['contactos'], message: 'El contacto de emergencia no puede tener tu mismo teléfono',
  });

export type DatosPaciente = z.input<typeof EsquemaPaciente>;
export type Paciente = z.output<typeof EsquemaPaciente>;
export type ErroresPaciente = Partial<Record<string, string[]>>;

/** Convierte un FormData plano (contactos.0.nombre, …) en el objeto que valida el esquema. */
export function desdeFormData(datos: FormData): Record<string, unknown> {
  const contactos: Record<string, string>[] = [];
  const plano: Record<string, unknown> = {};
  for (const [clave, valor] of datos.entries()) {
    if (typeof valor !== 'string' || clave.startsWith('$ACTION')) continue;
    const m = /^contactos\.(\d+)\.(\w+)$/.exec(clave);
    if (m) (contactos[Number(m[1])] ??= {})[m[2]!] = valor;
    else plano[clave] = valor;
  }
  return { ...plano, aceptaDatos: plano.aceptaDatos === 'on' || plano.aceptaDatos === 'true', contactos: contactos.filter(Boolean) };
}

/** Errores de zod aplanados con rutas de punto ("contactos.0.telefono"), para asignarlos a su campo. */
export function erroresPorCampo(error: z.ZodError): ErroresPaciente {
  const errores: ErroresPaciente = {};
  for (const i of error.issues) (errores[i.path.join('.') || 'formulario'] ??= []).push(i.message);
  return errores;
}
