# syntax=docker/dockerfile:1

FROM node:20-alpine AS base
WORKDIR /workspace

FROM base AS dev
# package.json is copied (not bind-mounted) so `npm install` bakes node_modules into the
# image; docker-compose then bind-mounts the rest of the source over this layer and uses a
# named volume for /workspace/node_modules so the bind mount doesn't hide what we installed.
COPY package.json package-lock.json* ./
RUN npm install
ENV NODE_ENV=development
CMD ["npm", "run", "dev"]

FROM base AS build
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
RUN npm run build

FROM node:20-alpine AS runtime
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build /workspace/package.json /workspace/package-lock.json* ./
RUN npm install --omit=dev
COPY --from=build /workspace/dist ./dist
COPY --from=build /workspace/views ./views
USER appuser
EXPOSE 3000
ENTRYPOINT ["node", "dist/server.js"]
