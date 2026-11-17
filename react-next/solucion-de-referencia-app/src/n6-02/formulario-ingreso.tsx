'use client';
import { useActionState } from 'react';
import { ingresar, type EstadoIngreso } from '@talleres/react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/acciones-ingreso';

export function FormularioIngreso({ retorno }: { retorno?: string }) {
  const [estado, accion, pendiente] = useActionState<EstadoIngreso, FormData>(ingresar, {});
  return (
    <form action={accion}>
      <input type="hidden" name="retorno" value={retorno ?? ''} />
      <p><label>Correo <input name="correo" type="email" autoComplete="username" required /></label></p>
      <p><label>Clave <input name="clave" type="password" autoComplete="current-password" required /></label></p>
      <button type="submit" disabled={pendiente}>Ingresar</button>
      {estado.error && <p role="alert">{estado.error}</p>}
    </form>
  );
}
