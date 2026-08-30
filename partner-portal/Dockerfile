# Expects dist/ to be pre-built before docker build
# CI: downloaded from the build job artifact; local: run npm run build first
FROM nginx:1.31-alpine
COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
