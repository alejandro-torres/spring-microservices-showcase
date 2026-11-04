# curl examples

A tour of the API with `curl` through the gateway: getting a token, paginated listings, each error code in English and Spanish, logout, finding a trace in Zipkin and following an order through RabbitMQ.

Requirements:
- The system started with `docker compose up -d --build`; see the [README](../README.md).
- Bash with `curl` and `openssl` (on Windows, Git Bash).

All examples use the data of the `dev` profile.

> In Git Bash for Windows, JSON bodies with accented characters can arrive in another encoding and the API responds `400 MALFORMED_REQUEST`. That is why the examples use none.

```bash
GW=http://localhost:8080
```

> **On Kubernetes** the API is at `https://showcase.localhost`, with a certificate signed by a local CA. The examples work the same with `GW=https://showcase.localhost` and curl trusting that CA (on Windows, also `--ssl-no-revoke`):
>
> ```bash
> kubectl -n cert-manager get secret showcase-ca -o jsonpath='{.data.ca\.crt}' | base64 -d > showcase-ca.crt
> GW=https://showcase.localhost
> alias curl='curl --cacert showcase-ca.crt --ssl-no-revoke'
> ```
>
> What changes is in sections 5 (traces are in Tempo, not Zipkin) and 6 (the RabbitMQ console goes through port-forward).

## 1. Get a token

> To try the API from the browser you need none of this: at http://localhost:8080/swagger-ui.html, *Authorize → oauth2* opens the login form and Swagger picks up the token on its own (see the [README](../README.md#api-documentation-swagger)).

From the terminal there are two ways:
- **Service token.** `client_credentials` with the `internal-service` client. It identifies a service, not a user, so it has no management permissions:

  ```bash
  curl -s -u internal-service:internal-dev-secret -d grant_type=client_credentials -d scope=internal $GW/oauth2/token
  ```

- **User token.** `authorization_code` with PKCE and the `web-client` client, the same flow a browser follows:
  1. `/oauth2/authorize` redirects to the login form.
  2. The form is submitted with its CSRF token.
  3. auth-service redirects to the `redirect_uri` with a code.
  4. The code, together with the `code_verifier`, is exchanged for the token.

  This function does the four steps (copy it into the terminal):

```bash
login() {
  local gw=${GW:-http://localhost:8080} redirect=http://127.0.0.1:3000/callback
  local jar verifier challenge login_url csrf authorize_url code response token
  jar=$(mktemp)
  verifier=$(openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n' | cut -c1-64)
  challenge=$(printf '%s' "$verifier" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')
  # 1. /oauth2/authorize without a session redirects to the login form
  login_url=$(curl -s -c "$jar" -b "$jar" -o /dev/null -w '%{redirect_url}' -G "$gw/oauth2/authorize" \
    --data-urlencode response_type=code --data-urlencode client_id=web-client \
    --data-urlencode 'scope=openid profile' --data-urlencode "redirect_uri=$redirect" \
    --data-urlencode "code_challenge=$challenge" --data-urlencode code_challenge_method=S256 \
    --data-urlencode state=curl)
  # 2. the form carries a CSRF token
  csrf=$(curl -s -c "$jar" -b "$jar" "$login_url" | sed -n 's/.*name="_csrf"[^>]*value="\([^"]*\)".*/\1/p' | head -1)
  # 3. login → back to /oauth2/authorize → redirects to the callback with the code
  authorize_url=$(curl -s -c "$jar" -b "$jar" -o /dev/null -w '%{redirect_url}' "$gw/login" \
    --data-urlencode "username=$1" --data-urlencode "password=$2" --data-urlencode "_csrf=$csrf")
  code=$(curl -s -c "$jar" -b "$jar" -o /dev/null -w '%{redirect_url}' "$authorize_url" \
    | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
  rm -f "$jar"
  [ -n "$code" ] || { echo "login failed for $1 (password or attempt limit?)" >&2; return 1; }
  # 4. code + code_verifier → access token
  response=$(curl -s "$gw/oauth2/token" -d grant_type=authorization_code -d client_id=web-client \
    --data-urlencode "code=$code" --data-urlencode "redirect_uri=$redirect" \
    --data-urlencode "code_verifier=$verifier")
  token=$(printf '%s' "$response" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
  [ -n "$token" ] || { echo "$response" >&2; return 1; }
  echo "$token"
}
```

`admin` has the ADMIN and USER roles; `user01` only USER. Both use the development password `Showcase-Dev-2026!`.

```bash
TOKEN=$(login admin 'Showcase-Dev-2026!')
```
```bash
USER_TOKEN=$(login user01 'Showcase-Dev-2026!')
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/users/me
```

The login has its own limit: 5 requests in a row per IP and then one every 12 s. Each login spends two of those requests (`POST /login` and `POST /oauth2/token`). If you request several tokens in a row and the function prints a `429`, wait half a minute. The token lasts 15 minutes.

## 2. Paginated listings and filters

All listings accept these parameters:
- `page` and `size` (maximum 100).
- `sort` and `direction`.
- `includeInactive=true`, for ADMIN only.
- One or more `filter=field:operator:value`.

The allowed fields and operators of each resource are in the [README](../README.md).

Products between 500 and 1500 € in stock, from most to least expensive, 5 at a time:

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/v1/products?page=0&size=5&sort=price&direction=desc&filter=price:between:500,1500&filter=stock:gt:0"
```

Users with the ADMIN role, including deleted ones:

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/v1/users?filter=role:eq:ADMIN&includeInactive=true"
```

Customers from Madrid sorted by last name:

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/v1/customers?filter=city:eq:Madrid&sort=lastName"
```

The response carries the page and the totals:

```json
{"content":[{"id":56,"name":"Networking model 07","price":702.90,"stock":74, ...}],
 "page":0,"size":5,"totalElements":17,"totalPages":4,"first":true,"last":false}
```

## 3. Errors

All errors have the same format and the same `traceId` as the `X-Trace-Id` header. The message language depends on `Accept-Language`: English by default, Spanish with `Accept-Language: es`.

```json
{"timestamp":"2026-10-15T00:57:15.059Z","status":400,"error":"Bad Request","code":"FILTER_FIELD_NOT_ALLOWED",
 "message":"Filtering by 'password' is not allowed. Allowed fields: name, price, stock, categoryId, categoryName, createdAt.",
 "path":"/api/v1/products","traceId":"6ad024ea065fcb84c860cbb1f707cfd6"}
```

The 422 and order flow errors need a product with 1 unit and a customer with an address:

```bash
PRODUCT_ID=$(curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"curl test product","price":9.90,"stock":1,"categoryId":8}' $GW/api/v1/products | sed -n 's/^{"id":\([0-9]*\).*/\1/p')
```
```bash
CUSTOMER_ID=$(curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"firstName":"Ana","lastName":"Curl","email":"ana.curl@example.com","addresses":[{"street":"Gran Via 1","city":"Madrid","postalCode":"28013","country":"ES"}]}' $GW/api/v1/customers | sed -n 's/^{"id":\([0-9]*\).*/\1/p')
```

### 400 Bad Request

Filter by a field not on the whitelist (`FILTER_FIELD_NOT_ALLOWED`):

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/v1/products?filter=password:eq:x"
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Accept-Language: es" "$GW/api/v1/products?filter=password:eq:x"
```

```
Filtering by 'password' is not allowed. Allowed fields: name, price, stock, categoryId, categoryName, createdAt.
No se puede filtrar por 'password'. Campos permitidos: name, price, stock, categoryId, categoryName, createdAt.
```

Bean Validation (`VALIDATION_ERROR`). The detail of each field goes in `errors`:

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":""}' $GW/api/v1/categories
```

```json
{"status":400,"code":"VALIDATION_ERROR","message":"The request contains invalid data.",
 "errors":[{"field":"name","message":"is required and cannot be blank"},{"field":"name","message":"must be between 2 and 60 characters long"}], ...}
```

### 401 Unauthorized

Without a token (`UNAUTHORIZED`), or with a fake, expired or revoked token (`INVALID_TOKEN`):

```bash
curl -s $GW/api/v1/products
```
```bash
curl -s -H "Accept-Language: es" -H "Authorization: Bearer abc" $GW/api/v1/products
```

```
Authentication is required to access this resource.
El token de acceso no es válido, ha caducado o ha sido revocado.
```

### 403 Forbidden

`user01` does not have the `user:read` permission:

```bash
curl -s -H "Authorization: Bearer $USER_TOKEN" $GW/api/v1/users
```
```bash
curl -s -H "Accept-Language: es" -H "Authorization: Bearer $USER_TOKEN" $GW/api/v1/users
```

```
You do not have permission to perform this operation.
No tienes permiso para realizar esta operación.
```

### 404 Not Found

```bash
curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/products/999999
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Accept-Language: es" $GW/api/v1/products/999999
```

```
No active product with id 999999 was found.
No existe ningún producto activo con id 999999.
```

### 409 Conflict

Duplicate name (`CATEGORY_ALREADY_EXISTS`):

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"Networking"}' $GW/api/v1/categories
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Accept-Language: es" -H "Content-Type: application/json" -d '{"name":"Networking"}' $GW/api/v1/categories
```

```
A category named 'Networking' already exists.
Ya existe una categoría llamada 'Networking'.
```

Optimistic locking (`CONCURRENT_MODIFICATION`): category 8 is at version 0 and the request says it is modifying it from version 7.

```bash
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"Networking","description":"Routers","version":7}' $GW/api/v1/categories/8
```

```
This resource was modified by another user. Reload it and try again.
```

### 422 Unprocessable Content

Business rule (`INSUFFICIENT_STOCK`): an order of 5 units of a product that only has 1.

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "{\"customerId\":$CUSTOMER_ID,\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":5}]}" $GW/api/v1/orders
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Accept-Language: es" -H "Content-Type: application/json" -d "{\"customerId\":$CUSTOMER_ID,\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":5}]}" $GW/api/v1/orders
```

```
Not enough stock of product 67 (1 units left).
No hay stock suficiente del producto 67 (quedan 1 unidades).
```

### 423 Locked

The API returns 423 with `RESOURCE_BUSY` when it cannot get a resource's distributed lock. This only happens in the saga's stock reservation and return, which arrive through RabbitMQ and are retried, so no `curl` request causes it.

The other lock is that of an account (`ACCOUNT_LOCKED`) after 5 wrong passwords in a row. It looks like this:

```bash
jar=$(mktemp)
for i in 1 2 3 4 5; do
  csrf=$(curl -s -c $jar -b $jar $GW/login | sed -n 's/.*name="_csrf"[^>]*value="\([^"]*\)".*/\1/p' | head -1)
  curl -s -c $jar -b $jar -o /dev/null -w '%{http_code} %{redirect_url}\n' $GW/login --data-urlencode username=user02 --data-urlencode password=wrong --data-urlencode "_csrf=$csrf"
done
```

The form always responds `Invalid credentials`, even with the right password while the lock lasts (15 minutes). It does not say whether the account exists or is locked. The lock shows in the audit log and on the user:

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/v1/security-audit?filter=username:eq:user02&filter=eventType:eq:ACCOUNT_LOCKED"
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/v1/users?filter=username:eq:user02"
```

An ADMIN removes it early (`user02` has id 3):

```bash
curl -s -X PUT -H "Authorization: Bearer $TOKEN" $GW/api/v1/users/3/unlock
```

The `ACCOUNT_LOCKED` and `RESOURCE_BUSY` messages are in both languages:

```
The account 'user02' is locked until ... after too many failed sign-in attempts.
La cuenta de 'user02' está bloqueada hasta ... por demasiados intentos de inicio de sesión fallidos.
Resource {0} is being modified by another request. Please try again in a few seconds.
El recurso {0} está siendo modificado por otra petición. Inténtalo de nuevo en unos segundos.
```

### 429 Too Many Requests

Each user can make 20 requests per second, with bursts of up to 40. With 80 parallel requests, those beyond the burst get 429 and the `Retry-After` header:

```bash
seq 1 80 | xargs -P 40 -I{} curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" "$GW/api/v1/categories?size=1" | sort | uniq -c
```
```bash
curl -s -i -H "Authorization: Bearer $TOKEN" -H "Accept-Language: es" "$GW/api/v1/categories?size=1"
```

The two requests must go one right after the other: the bucket recovers in a couple of seconds.

```
You have exceeded the request limit. Please try again later.
Has superado el límite de peticiones. Inténtalo de nuevo más tarde.
```

### 500 Internal Server Error

It is returned by the last-resort handler on an unexpected error (`INTERNAL_ERROR`), with no internal Java details. No valid request causes it on purpose. Even with the database down the response is 503. It has the usual format:

```
Internal server error.
Error interno del servidor.
```

### 503 Service Unavailable

With a service stopped, the gateway responds through the circuit breaker, and so does order-service when it cannot query product-service:

```bash
docker compose stop product-service
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/products/1
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Accept-Language: es" -H "Content-Type: application/json" -d "{\"customerId\":$CUSTOMER_ID,\"items\":[{\"productId\":1,\"quantity\":1}]}" $GW/api/v1/orders
```
```bash
docker compose start product-service
```

```
The service is currently unavailable. Please try again later.
El servicio no está disponible en este momento. Inténtalo de nuevo más tarde.
```

## 4. Log out

`POST /api/v1/auth/logout` revokes the token immediately (it is stored in Redis until it expires):

```bash
curl -s -i -X POST -H "Authorization: Bearer $USER_TOKEN" $GW/api/v1/auth/logout
```
```bash
curl -s -H "Authorization: Bearer $USER_TOKEN" $GW/api/v1/users/me
```

The first responds `204 No Content` and the second `401 INVALID_TOKEN`.

## 5. Find a request in Zipkin

> On Kubernetes there is no Zipkin: traces are in Tempo. In Grafana, *Explore → Tempo → TraceQL* and paste the `traceId`, or search its logs in *Explore → Loki* with `{service=~".+"} | traceId="<traceId>"` ([docs/kubernetes.md](kubernetes.md#4-follow-a-request-by-its-traceid-grafana)).

Each response carries its `traceId` in the `X-Trace-Id` header, and in the body if it is an error. To create an order of 1 unit and keep its trace:

```bash
curl -s -i -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "{\"customerId\":$CUSTOMER_ID,\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":1}]}" $GW/api/v1/orders | grep -i "^x-trace-id\|^{"
```

The trace can be seen in two ways:
- In the browser: open http://localhost:9411, click *Search by trace ID* and paste the value.
- With the Zipkin API:

```bash
curl -s http://localhost:9411/api/v2/trace/<traceId> | grep -o '"serviceName":"[^"]*"' | sort | uniq -c
```

An order's trace goes through api-gateway, order-service, `order_db`, RabbitMQ, product-service, `product_db` and Redis (the stock lock). It shows the request, the SQL queries, the event send and its consumption. With the observability compose file, *Logs for this span* opens in Grafana the logs of that request across all services.

## 6. Follow an order through RabbitMQ

> On Kubernetes, the RabbitMQ console opens with `kubectl -n showcase port-forward svc/rabbitmq 15672` and the URLs in this section work the same.

The console is at http://localhost:15672 (user `admin`, password `admin-dev`). The exchanges are:
- `order.events`: `order.created` and `order.cancelled`.
- `product.events`: `stock.reserved` and `stock.reservation-failed`.
- `auth.events`.

Each service has its queues, for example `product-service.order-created` or `order-service.stock-reserved`, and each queue its `.dlq`.

An order starts `PENDING`, the saga reserves the stock and within a second or two it becomes `CONFIRMED`:

```bash
curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/orders/<id>
```

Messages are consumed immediately. To see one in the queue, stop its consumer:
1. Stop product-service and cancel the order. The `order.cancelled` event stays waiting in `product-service.order-cancelled`:

```bash
docker compose stop product-service
```
```bash
curl -s -X PUT -H "Authorization: Bearer $TOKEN" $GW/api/v1/orders/<id>/cancel
```
```bash
curl -s -u admin:admin-dev "http://localhost:15672/api/queues/%2F/product-service.order-cancelled?columns=name,messages_ready"
```

2. Look at the message without consuming it. It is the same as *Get messages* in the console, with *Ack mode: Nack message requeue true*:

```bash
curl -s -u admin:admin-dev -H "Content-Type: application/json" -d '{"count":1,"ackmode":"ack_requeue_true","encoding":"auto"}' "http://localhost:15672/api/queues/%2F/product-service.order-cancelled/get"
```

The message carries:
- The common envelope: `eventId`, `eventType`, `source`, `occurredAt`, `version` and `payload`.
- The `message_id`, which is the same `eventId`; the consumer uses it to avoid processing it twice.
- The `traceparent` header, which continues the request's trace in Zipkin.

```json
{"exchange":"order.events","routing_key":"order.cancelled",
 "properties":{"type":"order.cancelled","message_id":"b3a1db40-...","headers":{"traceparent":"00-6ad0265b4ffd5ba8...-01"}},
 "payload":"{\"source\":\"order-service\",\"eventId\":\"b3a1db40-...\",\"eventType\":\"order.cancelled\",\"payload\":{\"orderId\":122,\"lines\":[{\"productId\":67,\"quantity\":1}]}, ...}"}
```

3. Start product-service. It consumes the message, the queue returns to 0 and the product's stock returns to 1:

```bash
docker compose start product-service
```

Messages that fail after the retries end up in their queue's DLQ. They are viewed and re-sent from the gateway:

```bash
curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/admin/product-service/dead-letters
```
