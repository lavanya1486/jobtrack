# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build stage
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# The browser talks to nginx on the same origin, which proxies /api to the API
# container, so the built bundle only needs relative paths.
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ------------------------------------------------------------- runtime stage
FROM nginx:1.27-alpine AS runtime

# nginx.conf is a template: the official entrypoint renders it with envsubst at
# start-up, so BACKEND_URL can be changed without rebuilding the image. Only
# variables that exist in the environment are substituted, so nginx variables
# such as $uri survive.
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

ENV BACKEND_URL=http://backend:8080

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1/healthz | grep -q ok || exit 1
