#!/usr/bin/env bash
# Comprueba lo que el README de n6-01 dice de `ng update` a Angular 22: en un proyecto NUEVO de Angular 21 (fuera del repositorio)
# crea tres componentes (sin estrategia, Default y OnPush), ejecuta `ng update @angular/core@22 @angular/cli@22` y muestra en qué
# quedó cada uno. No forma parte de verificar.sh (necesita red y varios minutos);
# no deja registro.
set -euo pipefail
D="$(mktemp -d)"; trap 'rm -rf "$D"' EXIT; cd "$D"
export CI=true NG_CLI_ANALYTICS=false
npx -y @angular/cli@21 new demo --defaults --skip-git --skip-tests --style=css --routing=false --ssr=false --package-manager=npm >/dev/null 2>&1
cd demo
echo "Versión antes: $(node -p "require('./node_modules/@angular/core/package.json').version")"
mkdir -p src/app/c
cat > src/app/c/sin-estrategia.ts <<'T'
import { Component } from '@angular/core';
@Component({ selector: 'app-a', template: 'a' })
export class SinEstrategia {}
T
cat > src/app/c/por-defecto.ts <<'T'
import { ChangeDetectionStrategy, Component } from '@angular/core';
@Component({ selector: 'app-b', template: 'b', changeDetection: ChangeDetectionStrategy.Default })
export class PorDefecto {}
T
cat > src/app/c/on-push.ts <<'T'
import { ChangeDetectionStrategy, Component } from '@angular/core';
@Component({ selector: 'app-c', template: 'c', changeDetection: ChangeDetectionStrategy.OnPush })
export class OnPush {}
T
npx ng update @angular/core@22 @angular/cli@22 --force --allow-dirty 2>&1 | tail -15
echo "Versión después: $(node -p "require('./node_modules/@angular/core/package.json').version")"
for f in sin-estrategia por-defecto on-push; do echo "--- $f.ts"; grep -n "changeDetection\|ChangeDetectionStrategy" src/app/c/$f.ts || echo "(sin changeDetection)"; done
