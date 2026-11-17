// Punto 1, partida: un aviso con seis propiedades booleanas (combinaciones sin sentido: exito y error a la vez).
export interface AlertaAntesProps {
  texto: string;
  exito?: boolean; error?: boolean; advertencia?: boolean;
  conIcono?: boolean; descartable?: boolean; compacta?: boolean;
  onDescartar?: () => void;
}

export function AlertaAntes(p: AlertaAntesProps) {
  const clase = p.error ? 'alerta error' : p.advertencia ? 'alerta advertencia' : p.exito ? 'alerta exito' : 'alerta';
  return (
    <div className={clase} style={{ padding: p.compacta ? 2 : 8 }}>
      {p.conIcono && (p.error ? '⛔ ' : p.exito ? '✅ ' : 'ℹ️ ')}
      {p.texto}
      {p.descartable && <button type="button" onClick={p.onDescartar}>×</button>}
    </div>
  );
}
