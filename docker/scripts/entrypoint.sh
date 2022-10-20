#!/usr/bin/env bash

# Make sure the following variables are set:
# CA_CERTIFICATES
# DATABASE_URL a valid jdbc connection url
# DATABASE_NAME
# DATABASE_HOST
# DATABASE_PORT
# DATABASE_USERNAME
# DATABASE_PASSWORD

check_vars()
{
    var_names=("$@")
    for var_name in "${var_names[@]}"; do
        [ -z "${!var_name}" ] && echo "$var_name is unset." && var_unset=true
    done
    [ -n "$var_unset" ] && exit 1
    return 0
}

check_vars DATABASE_HOST DATABASE_PORT DATABASE_NAME DATABASE_PASSWORD DATABASE_URL DATABASE_USERNAME CA_CERTIFICATES

export SPRING_DATASOURCE_URL="${DATABASE_URL}"
export SPRING_DATASOURCE_USERNAME="${DATABASE_USERNAME}"
export SPRING_DATASOURCE_PASSWORD="${DATABASE_PASSWORD}"

JAVA_AGENT=""
if [[ -n "$APPLICATIONINSIGHTS_CONNECTION_STRING" ]]; then
  JAVA_AGENT="-javaagent:/app/applicationinsights-agent.jar"
fi

set -e
echo "V-Probematic starting - tag=${DOCKER_IMAGE_TAG} branch=${GIT_BRANCH} gitsha=${GIT_HASH} build_date=${BUILD_DATE}"

if [[ "$SSH_ENABLE" == "yes" ]]; then
  echo "Starting SSH on $SSH_PORT"
  sed -i "s/SSH_PORT/$SSH_PORT/g" /etc/ssh/sshd_config
  echo 'root:Docker!' | chpasswd
  /usr/sbin/sshd -t -d
  /usr/sbin/sshd
fi

echo "execing: " "java ${JAVA_OPTS} ${JAVA_AGENT} -jar /app/probematic-backend.jar"
exec /usr/sbin/gosu probematic java ${JAVA_OPTS} ${JAVA_AGENT} -jar /app/probematic-backend.jar
