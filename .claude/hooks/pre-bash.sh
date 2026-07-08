#!/usr/bin/env bash
# Recebe JSON via stdin com o comando que o Claude vai executar
TOOL_INPUT=$(cat)
CMD=$(echo "$TOOL_INPUT" | python3 -c "
import sys, json
print(json.load(sys.stdin).get('tool_input', {}).get('command', ''))
")

# Só nos interessa git commit
if ! echo "$CMD" | grep -q "git commit"; then
  exit 0
fi

MARKER=".claude/.tests-passed"

if [ ! -f "$MARKER" ]; then
  echo "BLOQUEADO: nenhum teste registrado. Rode antes:" >&2
  echo "  mvn test -pl <modulo-alterado>" >&2
  exit 1
fi

# Algum .java foi modificado depois do último teste que passou?
CHANGED=$(find . -path ./target -prune -o -name "*.java" -newer "$MARKER" -print 2>/dev/null | head -1)
if [ -n "$CHANGED" ]; then
  echo "BLOQUEADO: código Java modificado após o último teste:" >&2
  echo "  $CHANGED" >&2
  echo "Rode 'mvn test -pl <modulo>' novamente antes de commitar." >&2
  exit 1
fi

exit 0
