#!/usr/bin/env bash
# PostToolUse só dispara quando o comando Bash termina com sucesso (exit 0);
# falhas disparam PostToolUseFailure em vez deste hook. Por isso não há
# checagem de exit_code aqui — o próprio disparo já indica sucesso.
TOOL_INPUT=$(cat)
CMD=$(echo "$TOOL_INPUT" | python3 -c "
import sys, json
print(json.load(sys.stdin).get('tool_input', {}).get('command', ''))
")

# mvn test (com ou sem -pl) que terminou com sucesso → marca
if echo "$CMD" | grep -q "mvn .*test"; then
  touch .claude/.tests-passed
  echo "✓ Testes passaram — commit liberado" >&2
fi

exit 0
