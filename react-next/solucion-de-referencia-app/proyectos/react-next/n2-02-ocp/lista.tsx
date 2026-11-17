// ---- react-next/nivel-02-solid/02-ocp/README.md (bloque 4), copiado tal cual entre el contexto agregado ----
import type React from 'react';

interface ListaProps<T> {
  elementos: readonly T[];
  clave: (elemento: T) => string;
  children: (elemento: T) => React.ReactNode;
  vacio?: React.ReactNode;
}

export function Lista<T>({ elementos, clave, children, vacio = <p>Sin elementos.</p> }: ListaProps<T>) {
  if (elementos.length === 0) return <>{vacio}</>;
  return <ul>{elementos.map((e) => <li key={clave(e)}>{children(e)}</li>)}</ul>;
}
