'use client';
// Punto 2: useFormulario usado en dos formularios distintos, y el segundo también con React Hook Form para comparar.
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useFormulario } from '../hooks';
import { Campo } from '../../../nivel-02-solid/03-lsp/envoltorios';

export function FormularioContacto() {
  const [enviado, setEnviado] = useState<string | null>(null);
  const f = useFormulario({ nombre: '', correo: '' },
    (v) => ({ ...(v.nombre.trim() ? {} : { nombre: 'Escribe tu nombre' }), ...(v.correo.includes('@') ? {} : { correo: 'Correo inválido' }) }),
    (v) => setEnviado(v.nombre));
  return (
    <form onSubmit={f.enviar} noValidate>
      <Campo etiqueta="Nombre" {...f.campo('nombre')} />
      <Campo etiqueta="Correo de contacto" {...f.campo('correo')} />
      <button type="submit">Enviar contacto</button>
      {enviado && <p role="status">Gracias, {enviado}</p>}
    </form>
  );
}

export function FormularioClave() {
  const [listo, setListo] = useState(false);
  const f = useFormulario({ clave: '', repetir: '' },
    (v) => ({ ...(v.clave.length >= 12 ? {} : { clave: 'Mínimo 12 caracteres' }), ...(v.clave === v.repetir ? {} : { repetir: 'No coinciden' }) }),
    () => setListo(true));
  return (
    <form onSubmit={f.enviar} noValidate>
      <Campo etiqueta="Clave nueva" type="password" {...f.campo('clave')} />
      <Campo etiqueta="Repite la clave" type="password" {...f.campo('repetir')} />
      <button type="submit">Cambiar clave</button>
      {listo && <p role="status">Clave cambiada</p>}
    </form>
  );
}

export function FormularioClaveConBiblioteca() {
  const [listo, setListo] = useState(false);
  const { register, handleSubmit, watch, formState: { errors } } = useForm<{ clave: string; repetir: string }>({ mode: 'onBlur' });
  return (
    <form onSubmit={handleSubmit(() => setListo(true))} noValidate>
      <Campo etiqueta="Clave (biblioteca)" type="password" error={errors.clave?.message} {...register('clave', { minLength: { value: 12, message: 'Mínimo 12 caracteres' }, required: 'Mínimo 12 caracteres' })} />
      {/* eslint-disable-next-line react-hooks/incompatible-library -- watch() de react-hook-form no se puede memorizar; el compilador de React omite este componente a propósito */}
      <Campo etiqueta="Repite (biblioteca)" type="password" error={errors.repetir?.message} {...register('repetir', { validate: (v) => v === watch('clave') || 'No coinciden' })} />
      <button type="submit">Cambiar con biblioteca</button>
      {listo && <p role="status">Clave cambiada con biblioteca</p>}
    </form>
  );
}
