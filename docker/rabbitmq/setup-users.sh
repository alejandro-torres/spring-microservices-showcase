#!/bin/sh
# Un usuario de RabbitMQ por servicio, con permisos solo sobre sus exchanges y colas (API de administración).
# Las expresiones usan [.] en lugar de \. para no tener que escapar barras en el JSON.
#
# Permisos de RabbitMQ: configure = declarar o borrar; write = publicar en un exchange y enlazar una cola;
# read = consumir de una cola y enlazar desde un exchange. Cada consumidor declara también el exchange del que
# lee (configure) para no depender del orden de arranque, y reenvía mensajes de sus DLQ por amq.default (write).
set -eu

# En Kubernetes se llama al pod por un Service que también publica los pods aún no listos.
API="${RABBITMQ_API:-http://rabbitmq:15672/api}"
ADMIN="$RABBITMQ_ADMIN_USER:$RABBITMQ_ADMIN_PASSWORD"

create_user() {
  user="$1"; password="$2"; configure="$3"; write="$4"; read="$5"
  curl -fsS -u "$ADMIN" -X PUT "$API/users/$user" -H 'content-type: application/json' \
    -d "{\"password\":\"$password\",\"tags\":\"\"}"
  curl -fsS -u "$ADMIN" -X PUT "$API/permissions/%2F/$user" -H 'content-type: application/json' \
    -d "{\"configure\":\"$configure\",\"write\":\"$write\",\"read\":\"$read\"}"
  echo "Usuario $user creado"
}

create_user auth-service "$AUTH_RABBITMQ_PASSWORD" \
  '^auth[.]events$' \
  '^auth[.]events$' \
  '^$'

create_user product-service "$PRODUCT_RABBITMQ_PASSWORD" \
  '^(product[.]events|order[.]events|product-service[.].*)$' \
  '^(product[.]events|product-service[.].*|amq[.]default)$' \
  '^(order[.]events|product-service[.].*)$'

create_user order-service "$ORDER_RABBITMQ_PASSWORD" \
  '^(order[.]events|product[.]events|order-service[.].*)$' \
  '^(order[.]events|order-service[.].*|amq[.]default)$' \
  '^(product[.]events|order-service[.].*)$'

create_user customer-service "$CUSTOMER_RABBITMQ_PASSWORD" \
  '^(auth[.]events|customer-service[.].*)$' \
  '^(customer-service[.].*|amq[.]default)$' \
  '^(auth[.]events|customer-service[.].*)$'
