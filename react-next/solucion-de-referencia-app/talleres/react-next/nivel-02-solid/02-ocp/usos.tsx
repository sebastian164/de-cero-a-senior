'use client';
// Puntos 3 y 4: la lista genérica del README con dos tipos de datos, y el mapa de notificaciones con un tipo agregado.
import { useState } from 'react';
import { Lista } from '@/react-next/n2-02-ocp/lista';
import { Boton } from '@/react-next/n2-02-ocp/boton';
import { Alerta } from './alerta';
import { Tarjeta } from './tarjeta';
import { VistaNotificacionAmpliada, type NotificacionAmpliada } from './notificaciones';

interface Usuario { readonly id: string; readonly nombre: string; readonly correo: string }
interface Producto { readonly sku: string; readonly nombre: string; readonly precio: number }

const USUARIOS: readonly Usuario[] = [{ id: 'u1', nombre: 'Ana', correo: 'ana@correo.example' }, { id: 'u2', nombre: 'Luis', correo: 'luis@correo.example' }];
const PRODUCTOS: readonly Producto[] = [{ sku: 'CAF', nombre: 'Café', precio: 18000 }];
const NOTIFICACIONES: readonly NotificacionAmpliada[] = [
  { tipo: 'pedido', id: 'n1', numero: 'P-10' },
  { tipo: 'pago', id: 'n2', valor: 50000 },
  { tipo: 'alerta', id: 'n3', texto: 'Inventario bajo' },
  { tipo: 'envio', id: 'n4', guia: 'GU-778', transportadora: 'Servientrega' },
];

export function DemoOcp() {
  const [descartada, setDescartada] = useState(false);
  return (
    <div>
      <Tarjeta encabezado={<h3>Usuarios</h3>} pie={<small>{USUARIOS.length} usuarios</small>}>
        <Lista elementos={USUARIOS} clave={(u) => u.id}>{(u) => <>{u.nombre} &lt;{u.correo}&gt;</>}</Lista>
      </Tarjeta>
      <Tarjeta encabezado={<h3>Productos</h3>}>
        <Lista elementos={PRODUCTOS} clave={(p) => p.sku}>{(p) => <>{p.nombre}: {p.precio}</>}</Lista>
        <Lista elementos={[] as Producto[]} clave={(p) => p.sku} vacio={<p>Sin productos agotados.</p>}>{(p) => p.nombre}</Lista>
      </Tarjeta>
      <Tarjeta encabezado={<h3>Notificaciones</h3>}>
        {NOTIFICACIONES.map((n) => <VistaNotificacionAmpliada key={n.id} notificacion={n} />)}
      </Tarjeta>
      {!descartada && (
        <Alerta variante="exito" icono="✅" accion={<Boton aria-label="Descartar" onClick={() => setDescartada(true)}>×</Boton>}>
          Pedido guardado
        </Alerta>
      )}
      <Boton variante="peligro" inicio={<span aria-hidden="true">🗑 </span>}>Eliminar</Boton>
    </div>
  );
}
