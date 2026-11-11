#!/bin/sh
# Convierte DATABASE_URL de Render (postgres://user:pass@host:port/db?params)
# a JDBC + usuario/clave separados que Spring entiende.
# Sin esto, spring.datasource.url=${DATABASE_URL} falla (pgjdbc no parsea userinfo).
set -e
if [ -n "$DATABASE_URL" ]; then
  case "$DATABASE_URL" in
    jdbc:*)
      # Ya es JDBC, no tocar.
      ;;
    postgres://*|postgresql://*)
      _rest="${DATABASE_URL#*://}"
      _userinfo="${_rest%%@*}"
      _hostdb="${_rest#*@}"
      _user="${_userinfo%%:*}"
      _pass="${_userinfo#*:}"
      _hostport="${_hostdb%%/*}"
      _dbname="${_hostdb#*/}"
      export SPRING_DATASOURCE_URL="jdbc:postgresql://${_hostport}/${_dbname}"
      export SPRING_DATASOURCE_USERNAME="$_user"
      export SPRING_DATASOURCE_PASSWORD="$_pass"
      ;;
  esac
fi
exec java -jar /app/app.jar
