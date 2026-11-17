'use client';
import dynamic from 'next/dynamic';

export const DemoErrores = dynamic(() => import('./demo-errores-contenido'), { ssr: false, loading: () => <p>Cargando demostración…</p> });
