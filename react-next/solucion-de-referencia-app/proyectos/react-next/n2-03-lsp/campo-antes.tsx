// ---- react-next/nivel-02-solid/03-lsp/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
// Se exporta al final para poder probar lo que el README afirma que hace mal.
interface CampoProps {
  etiqueta: string;
  valor: string;
  alCambiar: (valor: string) => void;      // firma distinta a la del input nativo
}

function Campo({ etiqueta, valor, alCambiar }: CampoProps) {
  return (
    <div>
      <span>{etiqueta}</span>               {/* sin label asociado */}
      <input value={valor} onChange={(e) => alCambiar(e.target.value)} />
    </div>
  );
}

// No acepta disabled, required, type, name, autoComplete, onBlur, ref ni aria-*.
// No funciona con bibliotecas de formularios ni con formularios nativos.

export { Campo as CampoAntes };
