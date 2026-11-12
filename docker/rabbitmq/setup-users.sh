#!/bin/sh
# One RabbitMQ user per service, with permissions only over its exchanges and queues (management API).
# The expressions use [.] instead of \. to avoid escaping backslashes in the JSON.
#
# RabbitMQ permissions: configure = declare or delete; write = publish to an exchange and bind a queue;
# read = consume from a queue and bind from an exchange. Each consumer also declares the exchange it
# reads from (configure) so as not to depend on the startup order, and re-sends messages from its DLQs via amq.default (write).
set -eu

# In Kubernetes the pod is called through a Service that also publishes pods not yet ready.
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
