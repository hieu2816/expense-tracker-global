# ---- Stage 1: Build ----
FROM node:22-alpine AS build
WORKDIR /app
# Copy package files first (for dependency caching)
COPY package.json package-lock.json ./
# Install dependencies
RUN npm ci
# Copy source code
COPY . .
# Build for production (uses .env.production automatically)
RUN NODE_OPTIONS="--max-old-space-size=1024" npm run build

# ---- Stage 2: Runtime ----
FROM nginx:alpine
# Copy built files to Nginx's default serve directory
COPY --from=build /app/dist /usr/share/nginx/html

# Entrypoint: substitute ${TLS_DOMAIN} in the nginx.conf template, then start nginx.
# See docker-entrypoint.sh for why single quotes around '${TLS_DOMAIN}' matter for envsubst.
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80
ENTRYPOINT ["/docker-entrypoint.sh"]