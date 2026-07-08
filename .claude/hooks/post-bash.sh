#!/usr/bin/env bash
TOOL_INPUT=$(cat)
CMD=$(echo "$TOOL_INPUT" | python3 -c "
import sys, json
print(json.load(sys.stdin).get('tool_input', {}).get('command', ''))
")
EXIT_CODE=$(echo "$TOOL_INPUT" | python3 -c "
import sys, json
print(json.load(sys.stdin).get('tool_response', {}).get('exit_code', 1))
")

# mvn test (com ou sem -pl) que terminou com sucesso → marca
if echo "$CMD" | grep -q "mvn .*test" && [ "$EXIT_CODE" = "0" ]; then
  touch .claude/.tests-passed
  echo "✓ Testes passaram — commit liberado" >&2
fi

exit 0
