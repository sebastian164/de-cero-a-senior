'use client';
// Punto 2: versión con acción de servidor y useActionState. Funciona sin JavaScript (formulario HTML que envía a la acción).
// Punto 5: errores del servidor en su campo y foco en el primer error.
import { useActionState, useEffect, useRef } from 'react';
import { Campo } from '../../nivel-02-solid/03-lsp/envoltorios';
import { registrarPaciente, type EstadoRegistroClinica } from './acciones';
import { MAXIMO_CONTACTOS } from './esquema';

const texto = (v: unknown) => (typeof v === 'string' ? v : undefined);

export function FormularioClinicaAccion() {
  const [estado, accion, pendiente] = useActionState<EstadoRegistroClinica, FormData>(registrarPaciente, { contactos: 1 });
  const formulario = useRef<HTMLFormElement>(null);
  const v = estado.valores ?? {};
  const e = (campo: string) => estado.errores?.[campo]?.[0];
  const contactos = (v.contactos as Record<string, string>[] | undefined) ?? [];

  // Sincroniza el foco (DOM) con el resultado del servidor: lleva al usuario al primer campo con error.
  useEffect(() => {
    formulario.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  }, [estado.errores]);

  if (estado.registrado) return <p role="status">Paciente registrado: {estado.registrado}</p>;

  return (
    <form action={accion} ref={formulario} noValidate aria-label="Registro de paciente (acción de servidor)">
      <Campo etiqueta="Nombre completo" name="nombre" defaultValue={texto(v.nombre)} error={e('nombre')} autoComplete="name" />
      <Campo etiqueta="Documento" name="documento" inputMode="numeric" defaultValue={texto(v.documento)} error={e('documento')} />
      <Campo etiqueta="Fecha de nacimiento" name="nacimiento" type="date" defaultValue={texto(v.nacimiento)} error={e('nacimiento')} />
      <Campo etiqueta="Correo" name="correo" type="email" defaultValue={texto(v.correo)} error={e('correo')} autoComplete="email" />
      <Campo etiqueta="Celular" name="telefono" inputMode="tel" defaultValue={texto(v.telefono)} error={e('telefono')} />
      <Campo etiqueta="Acudiente (si eres menor de edad)" name="acudiente" defaultValue={texto(v.acudiente)} error={e('acudiente')} />
      <fieldset>
        <legend>Contactos de emergencia (mínimo 1, máximo {MAXIMO_CONTACTOS})</legend>
        {Array.from({ length: estado.contactos ?? 1 }, (_, i) => (
          <div key={i}>
            <Campo etiqueta={`Contacto ${i + 1}: nombre`} name={`contactos.${i}.nombre`} defaultValue={contactos[i]?.nombre} error={e(`contactos.${i}.nombre`)} />
            <Campo etiqueta={`Contacto ${i + 1}: celular`} name={`contactos.${i}.telefono`} defaultValue={contactos[i]?.telefono} error={e(`contactos.${i}.telefono`)} />
            <label>Contacto {i + 1}: parentesco{' '}
              <select name={`contactos.${i}.parentesco`} defaultValue={contactos[i]?.parentesco ?? ''}>
                <option value="">Elige…</option><option value="madre">Madre</option><option value="padre">Padre</option>
                <option value="pareja">Pareja</option><option value="hijo">Hijo</option><option value="otro">Otro</option>
              </select>
            </label>
            {e(`contactos.${i}.parentesco`) && <p role="alert">{e(`contactos.${i}.parentesco`)}</p>}
          </div>
        ))}
        {e('contactos') && <p role="alert">{e('contactos')}</p>}
        {(estado.contactos ?? 1) < MAXIMO_CONTACTOS && <button type="submit" name="intencion" value="agregar-contacto" formNoValidate>Agregar contacto</button>}
      </fieldset>
      <label><input type="checkbox" name="aceptaDatos" defaultChecked={v.aceptaDatos === true} aria-invalid={e('aceptaDatos') ? true : undefined} /> Autorizo el tratamiento de mis datos</label>
      {e('aceptaDatos') && <p role="alert">{e('aceptaDatos')}</p>}
      <p><button type="submit" name="intencion" value="registrar" disabled={pendiente}>{pendiente ? 'Registrando…' : 'Registrar paciente'}</button></p>
    </form>
  );
}
