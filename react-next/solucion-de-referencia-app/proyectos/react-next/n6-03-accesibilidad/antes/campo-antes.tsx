import { useId, type ComponentProps } from 'react';
// Apoyo (no está en el README): las propiedades del campo.
type CampoProps = { etiqueta: string; error?: string } & Omit<ComponentProps<'input'>, 'id'>;

function Campo({ etiqueta, error, ...resto }: CampoProps) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id}>{etiqueta}</label>
      <input id={id} aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined} {...resto} />
      {error && <p id={`${id}-error`} role="alert">{error}</p>}
    </div>
  );
}

export { Campo };
