#!/usr/bin/env bash

# The MySQL container restarts multiple times before it can be populated with data
# We need to wait for the schema to be imported. This script counts the tables that are present
#
# This scripts expects that the following variables are set
# CA_CERTIFICATES
# DATABASE_USERNAME
# DATABASE_PASSWORD
# DATABASE_HOST
# DATABASE_PORT
# DATABASE_NAME

# trap ctrl-c and call ctrl_c()
catch_signal () {
  echo "Signal caught... cleaning up"
rm -f /root/.my.cnf
  exit 1
}

trap 'catch_signal' SIGTERM SIGINT

COUNTER=1
MAXCOUNTER=6

# The following query counts the tables in the provided database schema

TABLES_IN_DB_QUERY=$(cat << EndOfMessage
SELECT
    CASE
        WHEN
            (
                SELECT COUNT(*) AS tables
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = '${DATABASE_NAME}'
            ) > 0
        THEN 0
        ELSE 1
    END AS tables_exist
EndOfMessage
)

# Query the database and return true if there are more than 0 tables.

tablesExist() {
    # shellcheck disable=SC2046
    return $(mysql --disable-column-names --batch -e "${TABLES_IN_DB_QUERY}")
}

if [[ ! -r ${CA_CERTIFICATES} ]]; then
    >&2 echo "The CA_CERTIFICATES bundle (${CA_CERTIFICATES}) does not exist or is not readable"
    exit 1
fi


cat > $HOME/.my.cnf <<EOF
[client]
user=${DATABASE_USERNAME}
host=${DATABASE_HOST}
port=${DATABASE_PORT}
password=${DATABASE_PASSWORD}
ssl-verify-server-cert
ssl_ca=${CA_CERTIFICATES}
protocol=TCP
connect-timeout=5
EOF


# uncomment these to troubleshoot
#set -x
# echo "Running mysql DNS test on ${DATABASE_HOST}"
# dig ${DATABASE_HOST}

echo "Running mysql connectivity check on ${DATABASE_HOST}"

while ! mysqladmin -v -v ping; do
    echo "Sleeping for 10 seconds before checking again if MySQL instance ${DATABASE_HOST} is available. [attempt ${COUNTER} of ${MAXCOUNTER}]"
    sleep 10
    COUNTER=$(( COUNTER + 1 ))
    if [ "${COUNTER}" -gt "${MAXCOUNTER}" ]; then
        >&2 echo "We have been waiting for MySQL too long already; failing."
        exit 1
    fi;
done

if [[ -n "$CHECK_MYSQL_TABLES_EXIST" ]]; then
    COUNTER=1
    echo "Running mysql tables check on ${DATABASE_HOST}"
    while ! tablesExist; do
        echo "Sleeping for 10 seconds before checking again if MySQL instance ${DATABASE_HOST} has tables created. [attempt ${COUNTER} of ${MAXCOUNTER}]"
        sleep 10
        COUNTER=$(( COUNTER + 1 ))
        if [ "${COUNTER}" -gt "${MAXCOUNTER}" ]; then
            >&2 echo "We have been waiting for MySQL tables too long already; failing."
            exit 1
        fi;
    done
fi

echo "Database connection successful!"

rm -f /root/.my.cnf
