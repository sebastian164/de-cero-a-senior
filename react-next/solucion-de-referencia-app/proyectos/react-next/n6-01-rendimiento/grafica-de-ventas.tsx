'use client';
// Contexto: la gráfica que se carga de forma diferida. Usa el canvas del navegador (por eso ssr: false en el README).
import { useEffect, useRef } from 'react';

const VENTAS = [12, 19, 7, 15, 22, 18, 25];

export default function GraficaDeVentas() {
  const lienzo = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const ctx = lienzo.current?.getContext('2d');
    if (!ctx) return;
    VENTAS.forEach((v, i) => { ctx.fillStyle = '#1f5fbf'; ctx.fillRect(10 + i * 40, 150 - v * 5, 28, v * 5); });
  }, []);
  return <canvas ref={lienzo} width={300} height={160} role="img" aria-label="Ventas de la semana: 12, 19, 7, 15, 22, 18 y 25" />;
}
