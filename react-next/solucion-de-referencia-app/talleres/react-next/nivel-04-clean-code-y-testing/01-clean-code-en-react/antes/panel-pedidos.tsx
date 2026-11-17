'use client';
// Taller nivel-04/01 — punto de partida: estado derivado, efectos que no sincronizan nada externo, banderas
// contradictorias, condiciones sin nombre, índice como clave, `&&` con un número y memorización "por si acaso".
// El linter debe quejarse de este archivo (se lintea aparte y se esperan errores: lo comprueba taller.test.tsx).
import { useCallback, useEffect, useMemo, useState } from 'react';

export interface Pedido { id: string; cliente: string; total: number; estado: 'pendiente' | 'pagado' | 'cancelado' }

export function PanelPedidosAntes({ cargar, usuario }: { cargar: () => Promise<Pedido[]>; usuario: { rol: string } | null }) {
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pedidos, setPedidos] = useState<Pedido[] | null>(null);
  const [total, setTotal] = useState(0);
  const [pendientes, setPendientes] = useState<Pedido[]>([]);
  const [enviado, setEnviado] = useState(false);
  const [filtro, setFiltro] = useState('');

  useEffect(() => {
    setCargando(true);
    cargar().then((p) => { setPedidos(p); setCargando(false); }).catch(() => { setError('No se pudieron cargar'); setCargando(false); });
  }, []);

  useEffect(() => {
    setTotal((pedidos ?? []).reduce((s, p) => s + p.total, 0));
    setPendientes((pedidos ?? []).filter((p) => p.estado === 'pendiente'));
  }, [pedidos]);

  useEffect(() => {
    if (enviado) { setPedidos((ps) => (ps ?? []).map((p) => (p.estado === 'pendiente' ? { ...p, estado: 'pagado' } : p))); setEnviado(false); }
  }, [enviado]);

  const filtrados = useMemo(() => (pedidos ?? []).filter((p) => p.cliente.includes(filtro)), [pedidos, filtro]);
  const cambiar = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setFiltro(e.target.value), []);

  return (
    <div>
      {cargando ? <p>Cargando…</p> : error ? <p>{error}</p> : (
        <div>
          <input value={filtro} onChange={cambiar} placeholder="Cliente" />
          {pendientes.length && <p>Pendientes por cobrar</p>}
          <ul>{filtrados.map((p, i) => <li key={i}>{p.cliente}: {p.total}</li>)}</ul>
          <p>Total: {total}</p>
          {usuario && usuario.rol === 'admin' && pendientes.length > 0 && !error && (
            <div onClick={() => { setEnviado(true); }}>Cobrar pendientes</div>
          )}
        </div>
      )}
    </div>
  );
}
