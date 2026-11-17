'use server';
// Punto 2 y 6: la acción de servidor valida SIEMPRE con el esquema, aunque la llamen sin pasar por el formulario.
import { EsquemaPaciente, MAXIMO_CONTACTOS, desdeFormData, erroresPorCampo, type DatosPaciente, type ErroresPaciente } from './esquema';
import { documentoRegistrado, guardarPaciente } from './pacientes';

export interface EstadoRegistroClinica {
  errores?: ErroresPaciente;
  valores?: Record<string, unknown>;
  contactos?: number;      // cuántas filas de contacto mostrar (sirve sin JavaScript)
  registrado?: string;
}

export async function registrarPaciente(previo: EstadoRegistroClinica, datos: FormData): Promise<EstadoRegistroClinica> {
  const entrada = desdeFormData(datos);
  const intencion = datos.get('intencion');
  const filas = Math.max(1, (entrada.contactos as unknown[]).length);

  // Mejora progresiva: sin JavaScript, "Agregar contacto" es otro botón de envío del mismo formulario.
  if (intencion === 'agregar-contacto') {
    return { valores: entrada, contactos: Math.min(MAXIMO_CONTACTOS, Math.max(filas, previo.contactos ?? 1) + 1) };
  }

  const resultado = EsquemaPaciente.safeParse(entrada);
  if (!resultado.success) return { errores: erroresPorCampo(resultado.error), valores: entrada, contactos: filas };
  if (await documentoRegistrado(resultado.data.documento)) {
    return { errores: { documento: ['Ya hay un paciente con ese documento'] }, valores: entrada, contactos: filas };
  }
  await guardarPaciente(resultado.data);
  return { registrado: resultado.data.nombre };
}

/** La misma regla para el formulario con biblioteca: recibe el objeto ya armado, valida de nuevo y no confía en él. */
export async function registrarPacienteObjeto(datos: DatosPaciente): Promise<EstadoRegistroClinica> {
  const resultado = EsquemaPaciente.safeParse(datos);
  if (!resultado.success) return { errores: erroresPorCampo(resultado.error) };
  if (await documentoRegistrado(resultado.data.documento)) return { errores: { documento: ['Ya hay un paciente con ese documento'] } };
  await guardarPaciente(resultado.data);
  return { registrado: resultado.data.nombre };
}
