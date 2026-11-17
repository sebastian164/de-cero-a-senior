// Apoyo (no está en el README): el icono.
const IconoGuardar = () => <svg aria-label="icono" role="img" />;

interface BotonProps {
  texto: string;
  primario?: boolean;
  peligro?: boolean;
  conIcono?: boolean;
  iconoDerecha?: boolean;
  cargando?: boolean;
  comoEnlace?: boolean;
  href?: string;
  // cada necesidad nueva agrega una propiedad y un if
}

function Boton(p: BotonProps) {
  if (p.comoEnlace) return <a href={p.href} className={p.primario ? 'btn primario' : 'btn'}>{p.texto}</a>;
  return (
    <button className={p.peligro ? 'btn peligro' : p.primario ? 'btn primario' : 'btn'}>
      {p.conIcono && !p.iconoDerecha && <IconoGuardar />}
      {p.cargando ? 'Cargando…' : p.texto}
      {p.conIcono && p.iconoDerecha && <IconoGuardar />}
    </button>
  );
}

export { Boton };
