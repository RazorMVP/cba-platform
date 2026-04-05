# CBA Deployment Reference

## Docker Compose (Local Development)

```yaml
# infrastructure/docker-compose.yml
version: '3.9'

services:
  postgres:
    image: postgres:16-alpine
    container_name: cba-postgres
    environment:
      POSTGRES_DB: cba_db
      POSTGRES_USER: cba_user
      POSTGRES_PASSWORD: ${DB_PASSWORD:-cba_pass}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U cba_user -d cba_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  keycloak:
    image: quay.io/keycloak/keycloak:23.0
    container_name: cba-keycloak
    command: start-dev --import-realm
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: ${KC_ADMIN_PASSWORD:-admin}
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/cba_db
      KC_DB_USERNAME: cba_user
      KC_DB_PASSWORD: ${DB_PASSWORD:-cba_pass}
      KC_HOSTNAME: localhost
    ports:
      - "8180:8080"
    volumes:
      - ./keycloak:/opt/keycloak/data/import
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "exec 3<>/dev/tcp/localhost/8080 && echo -e 'GET /health HTTP/1.1\r\nHost: localhost\r\n\r\n' >&3"]
      interval: 30s
      timeout: 10s
      retries: 10

  backend:
    build:
      context: ../backend
      dockerfile: Dockerfile
    container_name: cba-backend
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_PASSWORD: ${DB_PASSWORD:-cba_pass}
      ENCRYPTION_KEY: ${ENCRYPTION_KEY:-changeme-32-char-secret-key!!!!}
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: http://keycloak:8080/realms/cba
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      keycloak:
        condition: service_healthy

  web:
    build:
      context: ../web
      dockerfile: Dockerfile
    container_name: cba-web
    environment:
      API_URL: http://localhost:8080
      KEYCLOAK_URL: http://localhost:8180
    ports:
      - "4200:80"
    depends_on:
      - backend

  mailhog:
    image: mailhog/mailhog:latest
    container_name: cba-mailhog
    ports:
      - "1025:1025"   # SMTP
      - "8025:8025"   # Web UI
    profiles:
      - dev           # Only in dev profile

volumes:
  postgres_data:
```

## Backend Dockerfile

```dockerfile
# backend/Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S cba && adduser -S cba -G cba
COPY --from=builder /app/target/*.jar app.jar
USER cba
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Angular Dockerfile

```dockerfile
# web/Dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration=production

FROM nginx:alpine
COPY --from=builder /app/dist/cba-web /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

---

## Kubernetes (Production)

### Namespace

```yaml
# k8s/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: cba-platform
  labels:
    app.kubernetes.io/part-of: cba
```

### Backend Deployment

```yaml
# k8s/backend/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cba-backend
  namespace: cba-platform
spec:
  replicas: 2
  selector:
    matchLabels:
      app: cba-backend
  template:
    metadata:
      labels:
        app: cba-backend
    spec:
      containers:
        - name: backend
          image: cba/backend:latest
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: prod
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: cba-secrets
                  key: db-password
            - name: ENCRYPTION_KEY
              valueFrom:
                secretKeyRef:
                  name: cba-secrets
                  key: encryption-key
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 20
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: cba-backend
  namespace: cba-platform
spec:
  selector:
    app: cba-backend
  ports:
    - port: 8080
      targetPort: 8080
```

### HorizontalPodAutoscaler

```yaml
# k8s/backend/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: cba-backend-hpa
  namespace: cba-platform
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: cba-backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

### Ingress with TLS

```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: cba-ingress
  namespace: cba-platform
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
    - hosts:
        - banking.cba.com
        - auth.cba.com
      secretName: cba-tls
  rules:
    - host: banking.cba.com
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: cba-backend
                port:
                  number: 8080
          - path: /
            pathType: Prefix
            backend:
              service:
                name: cba-web
                port:
                  number: 80
    - host: auth.cba.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: cba-keycloak
                port:
                  number: 8080
```

### Secrets (use Sealed Secrets or Vault in real deployments)

```yaml
# k8s/secrets.yaml — DO NOT commit plaintext, use sealed-secrets or Vault
apiVersion: v1
kind: Secret
metadata:
  name: cba-secrets
  namespace: cba-platform
type: Opaque
stringData:
  db-password: "CHANGE_ME"
  encryption-key: "CHANGE_ME_32_CHAR_SECRET_KEY!!!!"
  keycloak-admin-password: "CHANGE_ME"
```

---

## Getting Started (Local)

```bash
# 1. Clone and enter the project
git clone <repo> cba-platform && cd cba-platform

# 2. Start infrastructure (Postgres + Keycloak)
docker-compose up postgres keycloak -d

# 3. Wait for Keycloak to be ready (~60 seconds), then start backend
docker-compose up backend -d

# 4. Start web portal
docker-compose up web -d

# 5. Access the services
open http://localhost:4200          # Angular web portal
open http://localhost:8080/swagger-ui.html  # API documentation
open http://localhost:8180          # Keycloak admin console
open http://localhost:8025          # MailHog (dev emails)

# Default credentials (from demo data)
# Admin: admin@cba.com / Admin@123
# Teller: teller@cba.com / Teller@123
# Customer: customer@cba.com / Customer@123
```

## Flutter Mobile Setup

```bash
cd mobile
flutter pub get
flutter run    # Connects to localhost:8080 in dev mode
```
