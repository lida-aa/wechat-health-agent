#!/usr/bin/env bash
set -euo pipefail

repository_root="$(git rev-parse --show-toplevel)"
script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script_path="$script_directory/$(basename "${BASH_SOURCE[0]}")"
script_relative_path="${script_path#"$repository_root"/}"
cd "$repository_root"

forbidden_path_pattern='(^|/)application\.(yaml|yml)$|(^|/)\.env$|(^|/)\.env\.(local|development|production|test)$|(^|/)(ilink-session[^/]*\.json|bili-cookie\.txt)$|(^|/)data/.*\.(db|sqlite|sqlite3)$|(^|/)health-data/|(^|/)src/main/resources/static/(images|files|voices)/'
secret_value_pattern='sk-[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{30,}|gh[pousr]_[A-Za-z0-9_]{30,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----'

tracked_forbidden_files="$(git ls-files | grep -E "$forbidden_path_pattern" || true)"
if [[ -n "$tracked_forbidden_files" ]]; then
  echo '::error::Sensitive runtime files are tracked by Git.'
  printf '%s\n' "$tracked_forbidden_files"
  echo 'Keep the local files, but remove them from Git tracking before pushing.'
  exit 1
fi

secret_matches="$(git grep -lEI "$secret_value_pattern" -- . \
  ":(exclude)$script_relative_path" || true)"
if [[ -n "$secret_matches" ]]; then
  echo '::error::Possible literal credential detected in tracked text.'
  echo 'Only file names are shown so credentials are not copied into CI logs.'
  printf '%s\n' "$secret_matches"
  exit 1
fi

echo 'Sensitive-file and literal-credential checks passed.'
