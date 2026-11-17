'use client';
// Ensamblaje real, una vez, en la raíz de la parte de cliente (README: ProveedorDeDependencias).
import { useSyncExternalStore } from 'react';
import { BotonAgregar, ProveedorDeDependencias } from '@/react-next/n2-05-dip/dependencias';
import { almacenLocal, analiticaGoogle } from '@/react-next/n2-05-dip/implementaciones';

const suscribir = (avisar: () => void) => { window.addEventListener('carrito-dip', avisar); return () => window.removeEventListener('carrito-dip', avisar); };

function Contador() {
  const n = useSyncExternalStore(suscribir, () => (localStorage.getItem('carrito-dip') ? JSON.parse(localStorage.getItem('carrito-dip')!).length : 0), () => 0);
  return <p role="status">{n} líneas guardadas en localStorage</p>;
}

export function DemoDip() {
  return (
    <ProveedorDeDependencias valor={{
      almacen: { leer: almacenLocal.leer, guardar: (l) => { almacenLocal.guardar(l); window.dispatchEvent(new Event('carrito-dip')); } },
      analitica: analiticaGoogle,
      ahora: Date.now,
    }}>
      <BotonAgregar producto={{ id: 'p1', nombre: 'Café', precio: 18000 }} />
      <Contador />
    </ProveedorDeDependencias>
  );
}
