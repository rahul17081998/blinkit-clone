# Eureka — Service Discovery

## What is Eureka?

Eureka is a **service discovery server** from Netflix, integrated into Spring Cloud. It acts as a central registry where every microservice registers itself on startup and looks up other services by name at runtime.

In simple terms: **Eureka is the phone book of your microservices.**

---

## The Problem It Solves

In this project there are 12 microservices, each running on a different port. Services need to call each other — for example, `order-service` calls `inventory-service` to reserve stock.

### Without Eureka (hardcoded addresses)

```yaml
# order-service application.yml
inventory-service-url: http://localhost:8084
payment-service-url:   http://localhost:8086
```

This breaks the moment you:
- Deploy to Docker — `localhost` means nothing inside a container; each container gets its own dynamic IP
- Scale a service — run 2 instances of `inventory-service` on different ports; which one do you call?
- Change a port — every service that calls it needs to be updated manually

### With Eureka (dynamic discovery)

Every service registers itself with Eureka on startup:

```
inventory-service  →  "I'm alive at 172.18.0.5:8084"
payment-service    →  "I'm alive at 172.18.0.9:8086"
cart-service       →  "I'm alive at 172.18.0.7:8087"
```

When `order-service` wants to call `inventory-service`, it asks Eureka for the current address — no hardcoding needed.

---

## How It Works (Step by Step)

```
1. Eureka Server starts → dashboard available at localhost:8761

2. Each microservice starts → registers with Eureka:
   POST http://eureka-server:8761/eureka/apps/INVENTORY-SERVICE
   Body: { host, port, status: "UP" }

3. Eureka stores the registry:
   INVENTORY-SERVICE  →  172.18.0.5:8084
   PAYMENT-SERVICE    →  172.18.0.9:8086
   ...

4. Every 30 seconds, each service sends a heartbeat to Eureka:
   PUT http://eureka-server:8761/eureka/apps/INVENTORY-SERVICE/instance-id
   → Proves it is still alive

5. If heartbeat stops → Eureka removes the service from registry after ~90 seconds
   → No traffic is routed to a dead instance

6. When order-service needs to call inventory-service:
   → Asks Eureka: "Where is INVENTORY-SERVICE?"
   → Gets back: "172.18.0.5:8084"
   → Makes the call
```

---

## Usage in This Project

### 1. API Gateway routes use `lb://` (load-balanced URI)

```yaml
# api-gateway application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: inventory-service
          uri: lb://inventory-service    # lb = ask Eureka, then load balance
          predicates:
            - Path=/api/inventory/**
          filters:
            - StripPrefix=1
```

`lb://` tells Spring Cloud Gateway to resolve the service name through Eureka at request time.

### 2. Feign clients use service name, not URL

```java
// order-service calling inventory-service
@FeignClient(name = "inventory-service")   // service name registered in Eureka
public interface InventoryClient {

    @PostMapping("/inventory/reserve")
    ReserveResponse reserveStock(@RequestBody ReserveRequest request);

    @PostMapping("/inventory/release")
    void releaseStock(@RequestBody ReleaseRequest request);
}
```

No `localhost:8084` anywhere. Spring Cloud LoadBalancer asks Eureka for the current address and picks an instance if multiple are running.

### 3. Every service registers on startup

```yaml
# application.yml — same pattern for all services
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka}
  instance:
    prefer-ip-address: true
```

```java
// Main application class of every service
@SpringBootApplication
@EnableDiscoveryClient          // registers this service with Eureka
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
```

### 4. Eureka Server setup

```java
// eureka-server main class
@SpringBootApplication
@EnableEurekaServer             // turns this app into the registry
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

```yaml
# eureka-server application.yml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  client:
    register-with-eureka: false   # server does not register itself
    fetch-registry: false         # server does not fetch registry from another server
  server:
    wait-time-in-ms-when-sync-empty: 0
```

---

## What Eureka Gives You

| Problem | How Eureka Solves It |
|---|---|
| Services deployed in Docker with dynamic IPs | Services register by name; actual IP looked up at runtime |
| Running 2 instances of a service | Eureka holds both; load balancer picks one (round-robin) |
| A service crashes | Heartbeat stops → removed from registry → no traffic sent to dead instance |
| New service added | Just starts, registers automatically — no config change elsewhere |
| Port changes | Only the service itself needs to update its config |

---

## Feign Clients in This Project

Every synchronous inter-service call goes through Eureka:

| Caller | Calls | Purpose |
|---|---|---|
| `api-gateway` | all services | route + load balance all incoming requests |
| `order-service` | `inventory-service` | reserve / release / confirm stock |
| `order-service` | `coupon-service` | validate coupon + record usage |
| `order-service` | `cart-service` | fetch cart contents on checkout |
| `cart-service` | `product-service` | get current price + availability |
| `cart-service` | `coupon-service` | validate promo code |
| `review-service` | `product-service` | update avgRating + reviewCount |
| `review-service` | `order-service` | verify order is DELIVERED before allowing review |
| `delivery-service` | `user-service` | get customer delivery address |

---

## Service Registry at Runtime

When all services are running you can view the live registry at:

```
http://localhost:8761
```

You will see all registered services, their instance IDs, IP addresses, ports, and health status.

---

## Docker Note

Inside Docker all services share the `blinkit-network` bridge network. The Eureka server container is named `blinkit-eureka`, so every service connects using:

```yaml
EUREKA_SERVER_URL: http://eureka-server:8761/eureka
```

The container name `eureka-server` is Docker's internal DNS — it resolves to the correct container IP automatically. `localhost:8761` would NOT work inside Docker.

---

## Startup Order

Eureka must be healthy before any other service starts. The `docker-compose.yml` enforces this:

```yaml
auth-service:
  depends_on:
    eureka-server:
      condition: service_healthy   # waits until Eureka passes health check
```

All 12 application services have this dependency so they only start after Eureka is ready to accept registrations.
