// ---- react-next/nivel-04-clean-code-y-testing/02-formularios-y-validacion/README.md (bloque 4), copiado tal cual entre el contexto agregado ----
// registro/formulario-en-vivo.tsx
'use client';

export function FormularioRegistroEnVivo() {
  const { register, handleSubmit, formState: { errors, isSubmitting }, setError } =
    useForm<DatosRegistro>({ resolver: zodResolver(EsquemaRegistro), mode: 'onBlur' });

  const enviar = handleSubmit(async (datos) => {
    const resultado = await registrarEnServidor(datos);
    if (resultado.errores) {
      Object.entries(resultado.errores).forEach(([campo, mensajes]) =>
        setError(campo as keyof DatosRegistro, { message: mensajes?.[0] }));     // errores del servidor al campo
    }
  });

  return (
    <form onSubmit={enviar} noValidate>
      <Campo etiqueta="Correo" type="email" autoComplete="email" {...register('correo')} error={errors.correo?.message} />
      <Campo etiqueta="Documento" inputMode="numeric" {...register('documento')} error={errors.documento?.message} />
      <Campo etiqueta="Clave" type="password" autoComplete="new-password" {...register('clave')} error={errors.clave?.message} />
      <Campo etiqueta="Confirma la clave" type="password" autoComplete="new-password"
             {...register('confirmacion')} error={errors.confirmacion?.message} />
      <button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Registrando…' : 'Registrarme'}</button>
    </form>
  );
}

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { EsquemaRegistro, type DatosRegistro } from './esquema';
import { registrarEnServidor } from './acciones-en-vivo';
import { Campo } from '../../n2-03-lsp/campo';
