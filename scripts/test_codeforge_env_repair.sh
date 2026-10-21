#!/usr/bin/env bash
# Unit tests for codeforge-env repair and check_dpkg_consistency logic
set -euo pipefail

ENV_SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/libs/terminal-engine/src/main/assets/codeforge-env"

test_clean_state() {
  local tmp_bin
  tmp_bin=$(mktemp -d)
  trap 'rm -rf "$tmp_bin"' RETURN

  cat > "$tmp_bin/apt-get" <<'EOF'
#!/bin/sh
if [ "$1" = "check" ]; then
  exit 0
fi
exit 0
EOF
  chmod +x "$tmp_bin/apt-get"

  output=$(PATH="$tmp_bin:$PATH" bash "$ENV_SCRIPT" repair 2>&1)
  if ! echo "$output" | grep -q "Paketzustand ist konsistent, keine Reparatur erforderlich"; then
    echo "FAIL: Expected clean state message, got: $output"
    exit 1
  fi
  echo "PASS: Clean state repair test"
}

test_broken_unauthorized_package() {
  local tmp_bin
  tmp_bin=$(mktemp -d)
  trap 'rm -rf "$tmp_bin"' RETURN

  cat > "$tmp_bin/apt-get" <<'EOF'
#!/bin/sh
if [ "$1" = "check" ]; then
  echo "The following packages have unmet dependencies:"
  echo " unauthorized-pkg : Depends: something but it is not installed"
  echo "E: Unmet dependencies."
  exit 100
fi
exit 0
EOF
  chmod +x "$tmp_bin/apt-get"

  set +e
  output=$(PATH="$tmp_bin:$PATH" bash "$ENV_SCRIPT" repair --yes 2>&1)
  rc=$?
  set -e

  if [ "$rc" -eq 0 ]; then
    echo "FAIL: Expected repair to fail for unauthorized package, but succeeded"
    exit 1
  fi
  if ! echo "$output" | grep -q "Sicherheitsabbruch"; then
    echo "FAIL: Expected 'Sicherheitsabbruch' in output, got: $output"
    exit 1
  fi
  echo "PASS: Unauthorized package abort test"
}

test_broken_authorized_package() {
  local tmp_bin
  tmp_bin=$(mktemp -d)
  trap 'rm -rf "$tmp_bin"' RETURN

  cat > "$tmp_bin/apt-get" <<EOF
#!/bin/sh
for arg in "\$@"; do
  if [ "\$arg" = "-f" ]; then
    touch "$tmp_bin/.test_repaired"
    exit 0
  fi
done
for arg in "\$@"; do
  if [ "\$arg" = "check" ]; then
    if [ -f "$tmp_bin/.test_repaired" ]; then
      exit 0
    fi
    echo "The following packages have unmet dependencies:"
    echo " dpkg-perl : Depends: clang but it is not installed"
    echo "E: Unmet dependencies."
    exit 100
  fi
done
exit 0
EOF
  cat > "$tmp_bin/dpkg" <<EOF
#!/bin/sh
if [ "\$1" = "--remove" ]; then
  touch "$tmp_bin/.test_dpkg_removed"
  exit 0
fi
if [ "\$1" = "-s" ]; then
  exit 1
fi
exit 0
EOF
  chmod +x "$tmp_bin/apt-get" "$tmp_bin/dpkg"

  output=$(PATH="$tmp_bin:$PATH" bash "$ENV_SCRIPT" repair --yes 2>&1)

  if [ ! -f "$tmp_bin/.test_dpkg_removed" ]; then
    echo "FAIL: dpkg --remove was not invoked"
    exit 1
  fi
  if [ ! -f "$tmp_bin/.test_repaired" ]; then
    echo "FAIL: apt-get -f install was not invoked"
    exit 1
  fi
  if ! echo "$output" | grep -q "Reparatur erfolgreich abgeschlossen"; then
    echo "FAIL: Expected success message, got: $output"
    exit 1
  fi
  echo "PASS: Authorized package repair test"
}

test_setup_precheck_fails_cleanly() {
  local tmp_bin
  tmp_bin=$(mktemp -d)
  trap 'rm -rf "$tmp_bin"' RETURN

  cat > "$tmp_bin/apt-get" <<'EOF'
#!/bin/sh
if [ "$1" = "check" ]; then
  echo "The following packages have unmet dependencies:"
  echo " dpkg-perl : Depends: clang but it is not installed"
  echo "E: Unmet dependencies."
  exit 100
fi
exit 0
EOF
  chmod +x "$tmp_bin/apt-get"

  set +e
  output=$(PATH="$tmp_bin:$PATH" bash "$ENV_SCRIPT" setup 2>&1)
  rc=$?
  set -e

  if [ "$rc" -eq 0 ]; then
    echo "FAIL: Expected setup to fail due to broken dpkg state"
    exit 1
  fi
  if ! echo "$output" | grep -q "codeforge-env repair"; then
    echo "FAIL: Expected hint to run 'codeforge-env repair', got: $output"
    exit 1
  fi
  if ! echo "$output" | grep -q "Depends:"; then
    echo "FAIL: Expected Depends line in error output, got: $output"
    exit 1
  fi
  echo "PASS: Setup pre-check failure test"
}

test_clean_state
test_broken_unauthorized_package
test_broken_authorized_package
test_setup_precheck_fails_cleanly
echo "ALL TESTS PASSED"
