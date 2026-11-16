#!/usr/bin/env bash
# Reconstruye el historial de esta solución: aplica cada paso de ciclos/ sobre un repositorio git nuevo,
# ejecuta "mvn test", comprueba el resultado esperado (archivo ESPERADO) y hace un commit por paso
# con el texto de MENSAJE. Los pasos con un archivo SIN_COMMIT se ejecutan pero no entran al historial.
#
# Uso: ./ejecutar-ciclos.sh [carpeta-de-trabajo]
#   Sin argumento trabaja en una carpeta temporal que borra al terminar.
#   Con argumento deja ahí el repositorio para que explores el historial (git log, git show).
# Requisitos: JDK 21, Maven 3.9 o superior y git. Termina con código 0 solo si todos los pasos dan lo esperado.
set -u
AQUI="$(cd "$(dirname "$0")" && pwd)"
PASOS="$AQUI/ciclos"
if [ $# -ge 1 ]; then TRABAJO="$1"; CONSERVAR=1; else TRABAJO="$(mktemp -d)"; CONSERVAR=0; fi
git_c() { git -C "$TRABAJO" -c user.name=taller -c user.email=taller@example.invalid "$@"; }

# PASA | FALLA | NO_COMPILA | ERROR_DE_MAVEN
clasificar() {
    if [ "$2" = 0 ]; then echo PASA
    elif grep -q "COMPILATION ERROR\|Compilation failure" "$1"; then echo NO_COMPILA
    elif grep -q "There are test failures\|Tests run:.*\(Failures\|Errors\): [1-9]" "$1"; then echo FALLA
    else echo ERROR_DE_MAVEN; fi
}

if [ "$CONSERVAR" = 1 ] && [ -e "$TRABAJO" ] && { [ ! -d "$TRABAJO" ] || [ -n "$(ls -A "$TRABAJO" 2>/dev/null)" ]; }; then
    echo "La carpeta ya existe y no está vacía; indica una carpeta nueva: $TRABAJO" >&2; exit 2
fi
mkdir -p "$TRABAJO"
cp "$AQUI/pom.xml" "$TRABAJO/pom.xml"
printf 'target/\n.salida\n' > "$TRABAJO/.gitignore"
git init -q -b main "$TRABAJO"
git_c add -A && git_c commit -q -m "proyecto vacío"
errores=0
for paso in "$PASOS"/*/; do
    paso="${paso%/}"
    rm -rf "$TRABAJO/src" && cp -R "$paso/src" "$TRABAJO/src"
    esperado=$(cat "$paso/ESPERADO")
    (cd "$TRABAJO" && mvn -B -q test > "$TRABAJO/.salida" 2>&1); codigo=$?
    obtenido=$(clasificar "$TRABAJO/.salida" "$codigo")
    if [ "$obtenido" = "$esperado" ]; then
        printf '  ok   %-45s %s\n' "$(basename "$paso")" "$obtenido"
    else
        printf '  MAL  %-45s esperado %s, obtenido %s\n' "$(basename "$paso")" "$esperado" "$obtenido"
        tail -30 "$TRABAJO/.salida"; errores=$((errores + 1))
    fi
    rm -f "$TRABAJO/.salida"
    [ -f "$paso/SIN_COMMIT" ] && continue
    git_c add -A && git_c commit -q -m "$(cat "$paso/MENSAJE")"
done
echo "Historial:"
git_c log --reverse --format='  %s' | sed 1d

if [ "$CONSERVAR" = 1 ]; then echo "Repositorio de trabajo: $TRABAJO"; else rm -rf "$TRABAJO"; fi
if [ "$errores" -eq 0 ]; then echo "TODO PASÓ"; else echo "$errores paso(s) no dieron lo esperado"; fi
exit $((errores > 0))
