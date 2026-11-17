'use client';
// Contexto: sesión de cliente, HOC conSesion e Ingreso, para el bloque de patrones heredados.
import { createContext, useContext } from 'react';

export interface Sesion { readonly nombre: string }
export const ContextoSesion = createContext<Sesion | null>(null);
export const useSesion = () => useContext(ContextoSesion);
export const Ingreso = () => <p>Inicia sesión para continuar.</p>;

export function conSesion<P extends object>(Componente: React.ComponentType<P>) {
  function ConSesion(props: P) {
    const sesion = useSesion();
    return sesion ? <Componente {...props} /> : <Ingreso />;
  }
  ConSesion.displayName = `conSesion(${Componente.displayName ?? Componente.name})`;
  return ConSesion;
}
