'use client';
// Punto 3 (antes): el componente de cliente recibe el usuario completo. Todo cruza al navegador aunque no se muestre.
import type { Usuario } from '@/react-next/n2-04-isp/usuarios';

export function MenuCompleto({ usuario }: { usuario: Usuario }) {
  return <p>Hola, {usuario.nombres}</p>;
}
