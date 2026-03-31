#!/usr/bin/env bash
# Capture the exact bytes that `redis-cli --pipe` sends, including the terminator it appends.
# Needs Docker (for redis-cli) and nc. Usage: scripts/capture-pipe-bytes.sh [port]
set -euo pipefail
port="${1:-6390}"
out="$(mktemp -d)/pipe-bytes.bin"

nc -l "$port" > "$out" &
listener=$!
sleep 1

# Two RESP SET commands as the pipe's input.
printf '*3\r\n$3\r\nSET\r\n$2\r\nk0\r\n$1\r\n0\r\n*3\r\n$3\r\nSET\r\n$2\r\nk1\r\n$1\r\n1\r\n' |
  perl -e 'alarm 6; exec @ARGV' \
    docker run --rm -i redis:8 redis-cli -h host.docker.internal -p "$port" --pipe >/dev/null 2>&1 || true

kill "$listener" 2>/dev/null || true
xxd "$out"
