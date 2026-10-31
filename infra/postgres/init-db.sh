#!/bin/sh
# Exécuté une seule fois par l'image postgres (volume vide) : une base + un utilisateur par service.
set -e
for svc in auth catalog supplier sales reporting; do
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" <<EOF
CREATE USER ${svc}_user WITH PASSWORD '${DB_PASSWORD}';
CREATE DATABASE ${svc}_db OWNER ${svc}_user;
EOF
done
