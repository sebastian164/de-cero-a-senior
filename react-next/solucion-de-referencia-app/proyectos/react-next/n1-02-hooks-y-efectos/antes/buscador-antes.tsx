import { useEffect, useState } from 'react';
interface Producto { id: number; nombre: string }
export const expuesto: { resultados?: Producto[]; cantidad?: number } = {};

function Buscador({ categoria }: { categoria: string }) {
  const [texto, setTexto] = useState('');
  const [resultados, setResultados] = useState<Producto[]>([]);
  const [cantidad, setCantidad] = useState(0);

  useEffect(() => {
    setCantidad(resultados.length);                       // estado derivado sincronizado con un efecto
  }, [resultados]);

  useEffect(() => {
    fetch(`/api/productos?q=${texto}&c=${categoria}`)     // una petición por tecla
      .then((r) => r.json())
      .then(setResultados);                               // una respuesta lenta pisa a una reciente
  }, [texto]);                                            // falta 'categoria': datos obsoletos

  expuesto.resultados = resultados; expuesto.cantidad = cantidad;
  return <input value={texto} onChange={(e) => setTexto(e.target.value)} />;
}

export { Buscador };
