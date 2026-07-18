FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm install

# Vite loads frontend/.env during this build. If a local .env has not been
# created yet, use the committed example so a clean checkout still builds.
COPY . .
RUN if [ ! -f .env ]; then cp .env.example .env; fi && npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
