// ---- react-next/nivel-06-avanzado/01-rendimiento/README.md (bloque 1, desde '// Antes'), copiado tal cual entre el contexto agregado ----
// Antes: cada tecla vuelve a renderizar la tabla pesada
function Pagina() {
  const [texto, setTexto] = useState('');
  return (<><input value={texto} onChange={(e) => setTexto(e.target.value)} /><TablaPesada /></>);
}

import { useState } from 'react';
import { TablaPesada } from './contexto';
export { Pagina };
