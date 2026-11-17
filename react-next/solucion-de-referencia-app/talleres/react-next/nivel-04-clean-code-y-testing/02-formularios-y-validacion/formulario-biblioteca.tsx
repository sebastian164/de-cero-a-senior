'use client';
// Puntos 3, 4 y 5: React Hook Form con el mismo esquema, validación al salir del campo, lista dinámica de contactos
// (mínimo 1, máximo 3), errores del servidor asignados a su campo y foco en el primer error.
import { useState } from 'react';
import { useFieldArray, useForm, type FieldPath } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Campo } from '../../nivel-02-solid/03-lsp/envoltorios';
import { EsquemaPaciente, MAXIMO_CONTACTOS, type DatosPaciente, type Paciente } from './esquema';
import { registrarPacienteObjeto } from './acciones';

export function FormularioClinicaBiblioteca({ enviar = registrarPacienteObjeto }: { enviar?: typeof registrarPacienteObjeto }) {
  const [registrado, setRegistrado] = useState<string | null>(null);
  const { register, control, handleSubmit, setError, setFocus, formState: { errors, isSubmitting } } = useForm<DatosPaciente, unknown, Paciente>({
    resolver: zodResolver(EsquemaPaciente),
    mode: 'onBlur',
    shouldFocusError: true,
    defaultValues: { nombre: '', documento: '', correo: '', telefono: '', acudiente: '', contactos: [{ nombre: '', telefono: '', parentesco: 'madre' }] },
  });
  const contactos = useFieldArray({ control, name: 'contactos' });

  const alEnviar = handleSubmit(async (datos) => {
    const r = await enviar(datos);
    if (r.registrado) { setRegistrado(r.registrado); return; }
    const campos = Object.entries(r.errores ?? {});
    for (const [campo, mensajes] of campos) setError(campo as FieldPath<DatosPaciente>, { type: 'servidor', message: mensajes?.[0] });
    if (campos[0]) setFocus(campos[0][0] as FieldPath<DatosPaciente>);
  });

  if (registrado) return <p role="status">Paciente registrado: {registrado}</p>;

  return (
    <form onSubmit={alEnviar} noValidate aria-label="Registro de paciente (biblioteca)">
      <Campo etiqueta="Nombre completo" autoComplete="name" {...register('nombre')} error={errors.nombre?.message} />
      <Campo etiqueta="Documento" inputMode="numeric" {...register('documento')} error={errors.documento?.message} />
      <Campo etiqueta="Fecha de nacimiento" type="date" {...register('nacimiento')} error={errors.nacimiento?.message} />
      <Campo etiqueta="Correo" type="email" autoComplete="email" {...register('correo')} error={errors.correo?.message} />
      <Campo etiqueta="Celular" inputMode="tel" {...register('telefono')} error={errors.telefono?.message} />
      <Campo etiqueta="Acudiente (si eres menor de edad)" {...register('acudiente')} error={errors.acudiente?.message} />
      <fieldset>
        <legend>Contactos de emergencia (mínimo 1, máximo {MAXIMO_CONTACTOS})</legend>
        {contactos.fields.map((c, i) => (
          <div key={c.id}>
            <Campo etiqueta={`Contacto ${i + 1}: nombre`} {...register(`contactos.${i}.nombre`)} error={errors.contactos?.[i]?.nombre?.message} />
            <Campo etiqueta={`Contacto ${i + 1}: celular`} {...register(`contactos.${i}.telefono`)} error={errors.contactos?.[i]?.telefono?.message} />
            <label>Contacto {i + 1}: parentesco{' '}
              <select {...register(`contactos.${i}.parentesco`)}>
                <option value="madre">Madre</option><option value="padre">Padre</option><option value="pareja">Pareja</option>
                <option value="hijo">Hijo</option><option value="otro">Otro</option>
              </select>
            </label>{' '}
            <button type="button" onClick={() => contactos.remove(i)} disabled={contactos.fields.length <= 1}>Quitar contacto {i + 1}</button>
          </div>
        ))}
        {errors.contactos?.root?.message && <p role="alert">{errors.contactos.root.message}</p>}
        {errors.contactos?.message && <p role="alert">{errors.contactos.message}</p>}
        <button type="button" onClick={() => contactos.append({ nombre: '', telefono: '', parentesco: 'madre' })} disabled={contactos.fields.length >= MAXIMO_CONTACTOS}>
          Agregar contacto
        </button>
      </fieldset>
      <label><input type="checkbox" {...register('aceptaDatos')} aria-invalid={errors.aceptaDatos ? true : undefined} /> Autorizo el tratamiento de mis datos</label>
      {errors.aceptaDatos && <p role="alert">{errors.aceptaDatos.message}</p>}
      <p><button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Registrando…' : 'Registrar paciente'}</button></p>
    </form>
  );
}
