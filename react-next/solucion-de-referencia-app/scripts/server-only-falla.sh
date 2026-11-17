#!/usr/bin/env bash
# Taller nivel-05/01, punto 3: importar un módulo con `server-only` desde un componente de cliente rompe la construcción.
set -u
cd "$(dirname "$0")/.."
D="src/app/(principal)/zz-violacion"
mkdir -p "$D"
cat > "$D/page.tsx" <<'EOF'
'use client';
import { PRODUCTOS } from '@/react-next/n5-01-servidor-cliente/contexto';   // contexto.tsx empieza con import 'server-only'
export default function Pagina() { return <p>{PRODUCTOS.length}</p>; }
EOF
SALIDA=$(DIST_DIR=.next-violacion NEXT_TELEMETRY_DISABLED=1 npx next build 2>&1)
CODIGO=$?
rm -rf "$D" .next-violacion
echo "$SALIDA" | grep -iE "server-only|Server Component|Client Component" | head -5
if [ $CODIGO -ne 0 ] && echo "$SALIDA" | grep -qi "server-only"; then echo "OK: la construcción falla por server-only"; exit 0; fi
echo "FALLO: la construcción no falló por server-only (código $CODIGO)"; echo "$SALIDA" | tail -20; exit 1
