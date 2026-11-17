// Punto 1, después: una variante (unión) + huecos. Se extiende componiendo, sin abrir el componente.
import clsx from 'clsx';

type Variante = 'info' | 'exito' | 'advertencia' | 'error';

export type AlertaProps = Omit<React.ComponentProps<'div'>, 'role'> & {
  variante?: Variante;
  icono?: React.ReactNode;
  accion?: React.ReactNode;
};

const ROL: Record<Variante, 'status' | 'alert'> = { info: 'status', exito: 'status', advertencia: 'alert', error: 'alert' };

export function Alerta({ variante = 'info', icono, accion, children, className, ...resto }: AlertaProps) {
  return (
    <div role={ROL[variante]} className={clsx('alerta', `alerta-${variante}`, className)} {...resto}>
      {icono && <span aria-hidden="true">{icono} </span>}
      {children}
      {accion}
    </div>
  );
}
