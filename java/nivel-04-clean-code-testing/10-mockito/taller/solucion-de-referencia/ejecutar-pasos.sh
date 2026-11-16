#!/usr/bin/env bash
# Reconstruye el historial de esta solución: aplica cada paso de pasos/ sobre un repositorio git nuevo,
# ejecuta "mvn test", comprueba el resultado esperado (archivo ESPERADO) y hace un commit por paso
# con el texto de MENSAJE. Los pasos con un archivo SIN_COMMIT se ejecutan pero no entran al historial.
#
# Uso: ./ejecutar-pasos.sh [carpeta-de-trabajo]
#   Sin argumento trabaja en una carpeta temporal que borra al terminar.
#   Con argumento deja ahí el repositorio para que explores el historial (git log, git show).
# Requisitos: JDK 21, Maven 3.9 o superior y git. Termina con código 0 solo si todos los pasos dan lo esperado.
set -u
AQUI="$(cd "$(dirname "$0")" && pwd)"
PASOS="$AQUI/pasos"
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
# Comprobación extra: el Main de cada paso imprime lo mismo que el taller de DIP del Nivel 2.
enmascarar() { sed -E "s/[0-9]{4}-[0-9]{2}-[0-9]{2}/FECHA/g; s/ACME[- ][0-9]+/ACME-N/g; s/\\[ACME [0-9]+\\]/[ACME N]/"; }
DIP="$AQUI/../../../../nivel-02-solid/05-dip/taller/inicio"
if [ -f "$DIP/Main.java" ]; then
    SALIDA="$(mktemp -d)"
    (cd "$DIP" && java Main.java) 2>&1 | enmascarar > "$SALIDA/dip.txt"
    for p in "$PASOS"/*/; do
        [ -d "$p/src/main" ] || continue
        rm -rf "$SALIDA/clases"; mkdir -p "$SALIDA/clases"
        if javac -d "$SALIDA/clases" "$p"/src/main/java/suscripciones/*.java && java -cp "$SALIDA/clases" suscripciones.Main 2>&1 | enmascarar > "$SALIDA/paso.txt" && diff -q "$SALIDA/dip.txt" "$SALIDA/paso.txt" > /dev/null; then
            printf "  ok   %-45s Main imprime lo mismo que taller/inicio de DIP\\n" "$(basename "${p%/}")"
        else
            printf "  MAL  %-45s Main difiere del taller de DIP\\n" "$(basename "${p%/}")"; errores=$((errores + 1))
        fi
    done
    rm -rf "$SALIDA"
else
    echo "  --   comparación con el taller de DIP omitida: no se encontró nivel-02-solid/05-dip/taller/inicio"
fi
if [ "$CONSERVAR" = 1 ]; then echo "Repositorio de trabajo: $TRABAJO"; else rm -rf "$TRABAJO"; fi
if [ "$errores" -eq 0 ]; then echo "TODO PASÓ"; else echo "$errores paso(s) no dieron lo esperado"; fi
exit $((errores > 0))
