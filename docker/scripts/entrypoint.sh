#!/usr/bin/env bash

check_vars()
{
    var_names=("$@")
    for var_name in "${var_names[@]}"; do
        [ -z "${!var_name}" ] && echo "$var_name is unset." && var_unset=true
    done
    [ -n "$var_unset" ] && exit 1
    return 0
}

check_vars APP_SECRETS_FILE REDIS_PASS_FILE

JAVA_AGENT=""

echo "execing: " "java ${JAVA_OPTS} ${JAVA_AGENT} -jar /app/probematic.jar"
exec /usr/sbin/gosu probematic java ${JAVA_OPTS} ${JAVA_AGENT} -jar /app/probematic.jar
