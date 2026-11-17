'use client';
// Punto 5: este componente usa el hook de dominio usePedidos del README; no importa la biblioteca de datos.
import { usePedidos } from '@/react-next/n3-02-hooks/use-pedidos';
import { useTabla } from '../use-tabla';

export function TablaPedidos() {
  const { pedidos, cargando, error, recargar } = usePedidos({});
  const filas = pedidos.map((p) => ({ id: p.id, cliente: p.cliente, total: p.total }));
  const t = useTabla(filas, 3);
  if (cargando) return <p>Cargando pedidos…</p>;
  if (error) return <div role="alert">{error} <button type="button" onClick={() => recargar()}>Reintentar</button></div>;
  return (
    <div>
      <label>Filtrar por cliente <input value={t.texto} onChange={(e) => t.setTexto(e.target.value)} /></label>
      <table>
        <thead><tr>
          <th><button type="button" onClick={() => t.ordenarPor('cliente')}>Cliente</button></th>
          <th><button type="button" onClick={() => t.ordenarPor('total')}>Total</button></th>
        </tr></thead>
        <tbody>{t.elementos.map((f) => <tr key={f.id}><td>{f.cliente}</td><td>{f.total}</td></tr>)}</tbody>
      </table>
      <p>Página {t.pagina} de {t.totalPaginas} <button type="button" onClick={t.anterior}>Anterior</button> <button type="button" onClick={t.siguiente}>Siguiente</button></p>
    </div>
  );
}
