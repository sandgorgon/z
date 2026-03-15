#!/bin/sh
# Installer for z — Plan 9 Acme-inspired text editor
# Installs to ~/.local/lib/z/z.jar with a launcher at ~/.local/bin/z

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="${HOME}/.local/lib/z"
BIN_DIR="${HOME}/.local/bin"
ICON_DIR="${HOME}/.local/share/icons"
DESKTOP_DIR="${HOME}/.local/share/applications"

echo "Installing z..."
mkdir -p "${INSTALL_DIR}" "${BIN_DIR}" "${ICON_DIR}" "${DESKTOP_DIR}"

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

unzip -p "${INSTALL_DIR}/z.jar" images/z.png > "${ICON_DIR}/z.png"

cat > "${DESKTOP_DIR}/z.desktop" << DESKTOP
[Desktop Entry]
Name=z
Comment=Plan 9 Acme-inspired text editor
Exec=${BIN_DIR}/z %F
Icon=${ICON_DIR}/z.png
Type=Application
Categories=TextEditor;Development;
MimeType=text/plain;
Terminal=false
DESKTOP

echo ""
echo "Done."
echo "  JAR:      ${INSTALL_DIR}/z.jar"
echo "  Launcher: ${BIN_DIR}/z"
echo "  Icon:     ${ICON_DIR}/z.png"
echo "  Desktop:  ${DESKTOP_DIR}/z.desktop"
echo ""
echo "Make sure ${BIN_DIR} is on your PATH (it usually is by default), then run: z"
