#!/bin/sh
# Installer for z — Plan 9 Acme-inspired text editor
# Installs to ~/.local/lib/z/z.jar with a launcher at ~/bin/z

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="${HOME}/.local/lib/z"
BIN_DIR="${HOME}/bin"

echo "Installing z..."
mkdir -p "${INSTALL_DIR}" "${BIN_DIR}"

cp "${SCRIPT_DIR}/z.jar" "${INSTALL_DIR}/z.jar"

cat > "${BIN_DIR}/z" << 'LAUNCHER'
#!/bin/sh
exec java \
    -Dawt.useSystemAAFontSettings=on \
    -Dswing.aatext=true \
    -jar "${HOME}/.local/lib/z/z.jar" \
    "$@"
LAUNCHER

chmod +x "${BIN_DIR}/z"

echo ""
echo "Done."
echo "  JAR:      ${INSTALL_DIR}/z.jar"
echo "  Launcher: ${BIN_DIR}/z"
echo ""
echo "Make sure ${BIN_DIR} is on your PATH, then run: z"
