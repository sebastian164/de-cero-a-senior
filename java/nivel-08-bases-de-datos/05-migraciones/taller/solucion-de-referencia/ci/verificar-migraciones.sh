#!/bin/bash
# Aplica todas las migraciones a una base recién creada y arranca la aplicación con ddl-auto=validate.
# Falla si una migración no aplica, si se editó una ya aplicada o si las entidades no coinciden con el esquema.
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -B -q test -Dtest=MigracionesEnBaseLimpiaTest -Dsurefire.failIfNoSpecifiedTests=false -Dpg.puerto="${PG_PUERTO:-43432}"
echo "Migraciones verificadas sobre base limpia"
