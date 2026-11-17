// ---- react-next/nivel-04-clean-code-y-testing/02-formularios-y-validacion/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
// registro/formulario.tsx
'use client';

export function FormularioRegistro() {
  const [estado, accion, pendiente] = useActionState(registrar, {});

  return (
    <form action={accion} noValidate>
      <Campo name="correo" type="email" etiqueta="Correo" autoComplete="email"
             defaultValue={estado.valores?.correo} error={estado.errores?.correo?.[0]} />
      <Campo name="documento" etiqueta="Documento" inputMode="numeric"
             defaultValue={estado.valores?.documento} error={estado.errores?.documento?.[0]} />
      <Campo name="clave" type="password" etiqueta="Clave" autoComplete="new-password"
             error={estado.errores?.clave?.[0]} />
      <Campo name="confirmacion" type="password" etiqueta="Confirma la clave" autoComplete="new-password"
             error={estado.errores?.confirmacion?.[0]} />

      {estado.mensaje && <p role="alert">{estado.mensaje}</p>}
      <button type="submit" disabled={pendiente}>{pendiente ? 'Registrando…' : 'Registrarme'}</button>
    </form>
  );
}

import { useActionState } from 'react';
import { registrar } from './acciones';
import { Campo } from '../../n2-03-lsp/campo';
