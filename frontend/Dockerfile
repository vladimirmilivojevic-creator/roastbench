# syntax=docker/dockerfile:1

# ---- build the static site ------------------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Empty API URL: the browser talks to the same origin and nginx forwards /api to the API container.
ARG VITE_API_URL=""
ARG VITE_BASE="/"
ENV VITE_API_URL=$VITE_API_URL VITE_BASE=$VITE_BASE
RUN npm run build

# ---- serve it -------------------------------------------------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
