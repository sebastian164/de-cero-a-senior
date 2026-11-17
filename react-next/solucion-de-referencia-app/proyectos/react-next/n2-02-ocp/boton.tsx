'use client';
// ---- react-next/nivel-02-solid/02-ocp/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
import clsx from 'clsx';

// Contexto para la línea de uso del README.
const IconoGuardar = () => <span aria-hidden="true">💾</span>;
const guardando = false;

type BotonProps = React.ComponentProps<'button'> & {
  variante?: 'primario' | 'secundario' | 'peligro';      // una unión: sin combinaciones imposibles
  inicio?: React.ReactNode;                              // huecos: cualquier contenido
  fin?: React.ReactNode;
};

export function Boton({ variante = 'secundario', inicio, fin, children, className, ...resto }: BotonProps) {
  return (
    <button type="button" className={clsx('btn', `btn-${variante}`, className)} {...resto}>
      {inicio}
      {children}
      {fin}
    </button>
  );
}

// Uso: se extiende componiendo, sin tocar Boton
<Boton variante="primario" inicio={<IconoGuardar />} disabled={guardando}>
  {guardando ? 'Guardando…' : 'Guardar'}
</Boton>
