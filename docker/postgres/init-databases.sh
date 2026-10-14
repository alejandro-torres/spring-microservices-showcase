#!/bin/sh
# Database per Service: una base de datos y un usuario propietario por microservicio, en el mismo servidor.
# Ningún servicio puede leer la base de datos de otro. Lo ejecuta PostgreSQL solo al crear el volumen.
set -eu

create_database() {
  user="$1"; password="$2"; database="$3"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE USER "$user" WITH PASSWORD '$password';
CREATE DATABASE "$database" OWNER "$user";
REVOKE ALL ON DATABASE "$database" FROM PUBLIC;
SQL
}

create_database auth "$AUTH_DB_PASSWORD" auth_db
create_database product "$PRODUCT_DB_PASSWORD" product_db
create_database customer "$CUSTOMER_DB_PASSWORD" customer_db
create_database "order" "$ORDER_DB_PASSWORD" order_db
