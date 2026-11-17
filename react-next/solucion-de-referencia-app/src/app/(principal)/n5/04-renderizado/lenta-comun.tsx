import { Suspense } from 'react';
import { esperar } from './datos';

export async function Lento() { await esperar(1500); return <p data-testid="lento">Sección lenta lista (1,5 s)</p>; }
export function Rapido() { return <p data-testid="rapido">Sección rápida: título y navegación</p>; }
export async function Todo() { await esperar(1500); return <><Rapido /><p data-testid="lento">Sección lenta lista (1,5 s)</p></>; }
export const Envuelto = ({ fallback, children }: { fallback: string; children: React.ReactNode }) => <Suspense fallback={<p>{fallback}</p>}>{children}</Suspense>;
