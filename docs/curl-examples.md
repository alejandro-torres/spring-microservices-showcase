# Ejemplos con curl

Recorrido por la API con `curl` a través del gateway: obtener un token, listados paginados, cada código de error en español y en inglés, cierre de sesión, buscar una traza en Zipkin y seguir un pedido por RabbitMQ.

Requisitos:
- El sistema arrancado con `docker compose up -d --build`; ver el [README](../README.md).
- Bash con `curl` y `openssl` (en Windows, Git Bash).

Todos los ejemplos usan los datos del perfil `dev`.

> En Git Bash para Windows, los cuerpos JSON con tildes o eñes pueden llegar con otra codificación y la API responde `400 MALFORMED_REQUEST`. Por eso los ejemplos no llevan tildes.

```bash
GW=http://localhost:8080
```

> **En Kubernetes** la API está en `https://showcase.localhost`, con un certificado firmado por una CA local. Los ejemplos valen igual con `GW=https://showcase.localhost` y curl confiando en esa CA (en Windows, además `--ssl-no-revoke`):
>
> ```bash
> kubectl -n cert-manager get secret showcase-ca -o jsonpath='{.data.ca\.crt}' | base64 -d > showcase-ca.crt
> GW=https://showcase.localhost
> alias curl='curl --cacert showcase-ca.crt --ssl-no-revoke'
> ```
>
> Lo que cambia está en las secciones 5 (las trazas están en Tempo, no en Zipkin) y 6 (la consola de RabbitMQ va por port-forward).

## 1. Obtener un token

> Para probar la API desde el navegador no hace falta nada de esto: en http://localhost:8080/swagger-ui.html, *Authorize → oauth2* abre el formulario de login y Swagger recoge el token solo (ver el [README](../README.md#documentación-de-la-api-swagger)).

Desde la terminal hay dos formas:
- **Token de servicio.** `client_credentials` con el cliente `internal-service`. Identifica a un servicio, no a un usuario, así que no tiene permisos de gestión:

  ```bash
  curl -s -u internal-service:internal-dev-secret -d grant_type=client_credentials -d scope=internal $GW/oauth2/token
  ```

- **Token de usuario.** `authorization_code` con PKCE y el cliente `web-client`, el mismo flujo que sigue un navegador:
  1. `/oauth2/authorize` redirige al formulario de login.
  2. El formulario se envía con su token CSRF.
  3. auth-service redirige al `redirect_uri` con un código.
  4. El código, junto con el `code_verifier`, se cambia por el token.

  Esta función hace los cuatro pasos (cópiala en la terminal):

```bash
login() {
  local gw=${GW:-http://localhost:8080} redirect=http://127.0.0.1:3000/callback
  local jar verifier challenge login_url csrf authorize_url code response token
  jar=$(mktemp)
  verifier=$(openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n' | cut -c1-64)
  challenge=$(printf '%s' "$verifier" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')
  # 1. /oauth2/authorize sin sesión redirige al formulario de login
  login_url=$(curl -s -c "$jar" -b "$jar" -o /dev/null -w '%{redirect_url}' -G "$gw/oauth2/authorize" \
    --data-urlencode response_type=code --data-urlencode client_id=web-client \
    --data-urlencode 'scope=openid profile' --data-urlencode "redirect_uri=$redirect" \
    --data-urlencode "code_challenge=$challenge" --data-urlencode code_challenge_method=S256 \
    --data-urlencode state=curl)
  # 2. el formulario lleva un token CSRF
  csrf=$(curl -s -c "$jar" -b "$jar" "$login_url" | sed -n 's/.*name="_csrf"[^>]*value="\([^"]*\)".*/\1/p' | head -1)
  # 3. login → vuelve a /oauth2/authorize → redirige al callback con el código
  authorize_url=$(curl -s -c "$jar" -b "$jar" -o /dev/null -w '%{redirect_url}' "$gw/login" \
    --data-urlencode "username=$1" --data-urlencode "password=$2" --data-urlencode "_csrf=$csrf")
  code=$(curl -s -c "$jar" -b "$jar" -o /dev/null -w '%{redirect_url}' "$authorize_url" \
    | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
  rm -f "$jar"
  [ -n "$code" ] || { echo "login fallido para $1 (¿contraseña o límite de intentos?)" >&2; return 1; }
  # 4. código + code_verifier → access token
  response=$(curl -s "$gw/oauth2/token" -d grant_type=authorization_code -d client_id=web-client \
    --data-urlencode "code=$code" --data-urlencode "redirect_uri=$redirect" \
    --data-urlencode "code_verifier=$verifier")
  token=$(printf '%s' "$response" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
  [ -n "$token" ] || { echo "$response" >&2; return 1; }
  echo "$token"
}
```

`admin` tiene los roles ADMIN y USER; `user01` solo USER. Los dos usan la contraseña de desarrollo `Showcase-Dev-2026!`.

```bash
TOKEN=$(login admin 'Showcase-Dev-2026!')
```
```bash
USER_TOKEN=$(login user01 'Showcase-Dev-2026!')
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/users/me
```

El login tiene su propio límite: 5 peticiones seguidas por IP y después una cada 12 s. Cada login gasta dos de esas peticiones (`POST /login` y `POST /oauth2/token`). Si pides varios tokens seguidos y la función escribe un `429`, espera medio minuto. El token dura 15 minutos.

## 2. Listados paginados y filtros

Todos los listados admiten estos parámetros:
- `page` y `size` (máximo 100).
- `sort` y `direction`.
- `includeInactive=true`, solo para ADMIN.
- Uno o varios `filter=campo:operador:valor`.

Los campos y operadores permitidos de cada recurso están en el [README](../README.md).

Productos entre 500 y 1500 € con stock, del más caro al más barato, de 5 en 5:

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/v1/products?page=0&size=5&sort=price&direction=desc&filter=price:between:500,1500&filter=stock:gt:0"
```

Usuarios con el rol ADMIN, incluidos los borrados:

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/v1/users?filter=role:eq:ADMIN&includeInactive=true"
```

Clientes de Madrid ordenados por apellido:

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/v1/customers?filter=city:eq:Madrid&sort=lastName"
```

La respuesta trae la página y los totales:

```json
{"content":[{"id":56,"name":"Redes modelo 07","price":702.90,"stock":74, ...}],
 "page":0,"size":5,"totalElements":17,"totalPages":4,"first":true,"last":false}
```

## 3. Errores

Todos los errores tienen el mismo formato y el mismo `traceId` que la cabecera `X-Trace-Id`. El idioma del mensaje depende de `Accept-Language`: español por defecto, inglés con `Accept-Language: en`.

```json
{"timestamp":"2026-10-15T00:57:15.059Z","status":400,"error":"Bad Request","code":"FILTER_FIELD_NOT_ALLOWED",
 "message":"No se puede filtrar por 'password'. Campos permitidos: name, price, stock, categoryId, categoryName, createdAt.",
 "path":"/api/v1/products","traceId":"6ad024ea065fcb84c860cbb1f707cfd6"}
```

Para los errores 422 y del flujo de pedidos hacen falta un producto con 1 unidad y un cliente con dirección:

```bash
PRODUCT_ID=$(curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"Producto de prueba curl","price":9.90,"stock":1,"categoryId":8}' $GW/api/v1/products | sed -n 's/^{"id":\([0-9]*\).*/\1/p')
```
```bash
CUSTOMER_ID=$(curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"firstName":"Ana","lastName":"Curl","email":"ana.curl@example.com","addresses":[{"street":"Gran Via 1","city":"Madrid","postalCode":"28013","country":"ES"}]}' $GW/api/v1/customers | sed -n 's/^{"id":\([0-9]*\).*/\1/p')
```

### 400 Bad Request

Filtro por un campo que no está en la lista blanca (`FILTER_FIELD_NOT_ALLOWED`):

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/v1/products?filter=password:eq:x"
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Accept-Language: en" "$GW/api/v1/products?filter=password:eq:x"
```

```
No se puede filtrar por 'password'. Campos permitidos: name, price, stock, categoryId, categoryName, createdAt.
Filtering by 'password' is not allowed. Allowed fields: name, price, stock, categoryId, categoryName, createdAt.
```

Bean Validation (`VALIDATION_ERROR`). El detalle de cada campo va en `errors`:

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":""}' $GW/api/v1/categories
```

```json
{"status":400,"code":"VALIDATION_ERROR","message":"La petición contiene datos no válidos.",
 "errors":[{"field":"name","message":"es obligatorio y no puede estar vacío"},{"field":"name","message":"debe tener entre 2 y 60 caracteres"}], ...}
```

### 401 Unauthorized

Sin token (`UNAUTHORIZED`), o con un token falso, caducado o revocado (`INVALID_TOKEN`):

```bash
curl -s $GW/api/v1/products
```
```bash
curl -s -H "Accept-Language: en" -H "Authorization: Bearer abc" $GW/api/v1/products
```

```
Es necesario autenticarse para acceder a este recurso.
The access token is invalid, expired or revoked.
```

### 403 Forbidden

`user01` no tiene el permiso `user:read`:

```bash
curl -s -H "Authorization: Bearer $USER_TOKEN" $GW/api/v1/users
```
```bash
curl -s -H "Accept-Language: en" -H "Authorization: Bearer $USER_TOKEN" $GW/api/v1/users
```

```
No tienes permiso para realizar esta operación.
You do not have permission to perform this operation.
```

### 404 Not Found

```bash
curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/products/999999
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Accept-Language: en" $GW/api/v1/products/999999
```

```
No existe ningún producto activo con id 999999.
No active product with id 999999 was found.
```

### 409 Conflict

Nombre duplicado (`CATEGORY_ALREADY_EXISTS`):

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"Redes"}' $GW/api/v1/categories
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Accept-Language: en" -H "Content-Type: application/json" -d '{"name":"Redes"}' $GW/api/v1/categories
```

```
Ya existe una categoría llamada 'Redes'.
A category named 'Redes' already exists.
```

Bloqueo optimista (`CONCURRENT_MODIFICATION`): la categoría 8 está en la versión 0 y la petición dice que la modifica desde la 7.

```bash
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"Redes","description":"Routers","version":7}' $GW/api/v1/categories/8
```

```
Otro usuario ha modificado este recurso. Vuelve a cargarlo e inténtalo de nuevo.
```

### 422 Unprocessable Content

Regla de negocio (`INSUFFICIENT_STOCK`): un pedido de 5 unidades de un producto que solo tiene 1.

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "{\"customerId\":$CUSTOMER_ID,\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":5}]}" $GW/api/v1/orders
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Accept-Language: en" -H "Content-Type: application/json" -d "{\"customerId\":$CUSTOMER_ID,\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":5}]}" $GW/api/v1/orders
```

```
No hay stock suficiente del producto 67 (quedan 1 unidades).
Not enough stock of product 67 (1 units left).
```

### 423 Locked

La API devuelve 423 con `RESOURCE_BUSY` cuando no consigue el lock distribuido de un recurso. Esto solo pasa en la reserva y devolución de stock de la saga, que llegan por RabbitMQ y se reintentan, así que no hay una petición `curl` que lo provoque.

El otro bloqueo es el de una cuenta (`ACCOUNT_LOCKED`) tras 5 contraseñas incorrectas seguidas. Se ve así:

```bash
jar=$(mktemp)
for i in 1 2 3 4 5; do
  csrf=$(curl -s -c $jar -b $jar $GW/login | sed -n 's/.*name="_csrf"[^>]*value="\([^"]*\)".*/\1/p' | head -1)
  curl -s -c $jar -b $jar -o /dev/null -w '%{http_code} %{redirect_url}\n' $GW/login --data-urlencode username=user02 --data-urlencode password=incorrecta --data-urlencode "_csrf=$csrf"
done
```

El formulario responde siempre `Invalid credentials`, también con la contraseña buena mientras dure el bloqueo (15 minutos). No dice si la cuenta existe ni si está bloqueada. El bloqueo se ve en la auditoría y en el usuario:

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/v1/security-audit?filter=username:eq:user02&filter=eventType:eq:ACCOUNT_LOCKED"
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/v1/users?filter=username:eq:user02"
```

Un ADMIN lo quita antes de tiempo (`user02` tiene el id 3):

```bash
curl -s -X PUT -H "Authorization: Bearer $TOKEN" $GW/api/v1/users/3/unlock
```

Los mensajes de `ACCOUNT_LOCKED` y `RESOURCE_BUSY` están en los dos idiomas:

```
La cuenta de 'user02' está bloqueada hasta ... por demasiados intentos de inicio de sesión fallidos.
The account 'user02' is locked until ... after too many failed sign-in attempts.
El recurso {0} está siendo modificado por otra petición. Inténtalo de nuevo en unos segundos.
Resource {0} is being modified by another request. Please try again in a few seconds.
```

### 429 Too Many Requests

Cada usuario puede hacer 20 peticiones por segundo, con ráfagas de hasta 40. Con 80 peticiones en paralelo, las que pasan de la ráfaga reciben 429 y la cabecera `Retry-After`:

```bash
seq 1 80 | xargs -P 40 -I{} curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" "$GW/api/v1/categories?size=1" | sort | uniq -c
```
```bash
curl -s -i -H "Authorization: Bearer $TOKEN" -H "Accept-Language: en" "$GW/api/v1/categories?size=1"
```

Las dos peticiones tienen que ir seguidas: el cubo se recupera en un par de segundos.

```
Has superado el límite de peticiones. Inténtalo de nuevo más tarde.
You have exceeded the request limit. Please try again later.
```

### 500 Internal Server Error

Lo devuelve el manejador de último recurso ante un error no previsto (`INTERNAL_ERROR`), sin detalles internos de Java. No hay ninguna petición válida que lo provoque a propósito. Incluso con la base de datos caída la respuesta es 503. Tiene el formato de siempre:

```
Error interno del servidor.
Internal server error.
```

### 503 Service Unavailable

Con un servicio parado, el gateway responde por el circuit breaker, y order-service también cuando no puede consultar a product-service:

```bash
docker compose stop product-service
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/products/1
```
```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "Accept-Language: en" -H "Content-Type: application/json" -d "{\"customerId\":$CUSTOMER_ID,\"items\":[{\"productId\":1,\"quantity\":1}]}" $GW/api/v1/orders
```
```bash
docker compose start product-service
```

```
El servicio no está disponible en este momento. Inténtalo de nuevo más tarde.
The service is currently unavailable. Please try again later.
```

## 4. Cerrar sesión

`POST /api/v1/auth/logout` revoca el token al momento (se guarda en Redis hasta que caduca):

```bash
curl -s -i -X POST -H "Authorization: Bearer $USER_TOKEN" $GW/api/v1/auth/logout
```
```bash
curl -s -H "Authorization: Bearer $USER_TOKEN" $GW/api/v1/users/me
```

La primera responde `204 No Content` y la segunda `401 INVALID_TOKEN`.

## 5. Buscar una petición en Zipkin

> En Kubernetes no hay Zipkin: las trazas están en Tempo. En Grafana, *Explore → Tempo → TraceQL* y pega el `traceId`, o busca sus logs en *Explore → Loki* con `{service=~".+"} | traceId="<traceId>"` ([docs/kubernetes.md](kubernetes.md#4-seguir-una-petición-por-su-traceid-grafana)).

Cada respuesta lleva su `traceId` en la cabecera `X-Trace-Id`, y en el cuerpo si es un error. Para crear un pedido de 1 unidad y quedarte con su traza:

```bash
curl -s -i -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "{\"customerId\":$CUSTOMER_ID,\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":1}]}" $GW/api/v1/orders | grep -i "^x-trace-id\|^{"
```

La traza se puede ver de dos formas:
- En el navegador: abre http://localhost:9411, pulsa *Search by trace ID* y pega el valor.
- Con la API de Zipkin:

```bash
curl -s http://localhost:9411/api/v2/trace/<traceId> | grep -o '"serviceName":"[^"]*"' | sort | uniq -c
```

La traza de un pedido pasa por api-gateway, order-service, `order_db`, RabbitMQ, product-service, `product_db` y Redis (el lock del stock). Se ve la petición, las consultas SQL, el envío del evento y su consumo. Con el compose de observabilidad, *Logs for this span* abre en Grafana los logs de esa petición en todos los servicios.

## 6. Seguir un pedido por RabbitMQ

> En Kubernetes, la consola de RabbitMQ se abre con `kubectl -n showcase port-forward svc/rabbitmq 15672` y las URLs de esta sección valen igual.

La consola está en http://localhost:15672 (usuario `admin`, contraseña `admin-dev`). Los exchanges son:
- `order.events`: `order.created` y `order.cancelled`.
- `product.events`: `stock.reserved` y `stock.reservation-failed`.
- `auth.events`.

Cada servicio tiene sus colas, por ejemplo `product-service.order-created` u `order-service.stock-reserved`, y cada cola su `.dlq`.

Un pedido nace `PENDING`, la saga reserva el stock y en uno o dos segundos pasa a `CONFIRMED`:

```bash
curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/orders/<id>
```

Los mensajes se consumen al momento. Para ver uno en la cola, para su consumidor:
1. Para product-service y cancela el pedido. El evento `order.cancelled` se queda esperando en `product-service.order-cancelled`:

```bash
docker compose stop product-service
```
```bash
curl -s -X PUT -H "Authorization: Bearer $TOKEN" $GW/api/v1/orders/<id>/cancel
```
```bash
curl -s -u admin:admin-dev "http://localhost:15672/api/queues/%2F/product-service.order-cancelled?columns=name,messages_ready"
```

2. Mira el mensaje sin consumirlo. Es lo mismo que *Get messages* en la consola, con *Ack mode: Nack message requeue true*:

```bash
curl -s -u admin:admin-dev -H "Content-Type: application/json" -d '{"count":1,"ackmode":"ack_requeue_true","encoding":"auto"}' "http://localhost:15672/api/queues/%2F/product-service.order-cancelled/get"
```

El mensaje lleva:
- El sobre común: `eventId`, `eventType`, `source`, `occurredAt`, `version` y `payload`.
- El `message_id`, que es el mismo `eventId`; el consumidor lo usa para no procesarlo dos veces.
- La cabecera `traceparent`, que continúa la traza de la petición en Zipkin.

```json
{"exchange":"order.events","routing_key":"order.cancelled",
 "properties":{"type":"order.cancelled","message_id":"b3a1db40-...","headers":{"traceparent":"00-6ad0265b4ffd5ba8...-01"}},
 "payload":"{\"source\":\"order-service\",\"eventId\":\"b3a1db40-...\",\"eventType\":\"order.cancelled\",\"payload\":{\"orderId\":122,\"lines\":[{\"productId\":67,\"quantity\":1}]}, ...}"}
```

3. Arranca product-service. Consume el mensaje, la cola vuelve a 0 y el stock del producto vuelve a 1:

```bash
docker compose start product-service
```

Los mensajes que fallan después de los reintentos acaban en la DLQ de su cola. Se consultan y se reenvían desde el gateway:

```bash
curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/admin/product-service/dead-letters
```
