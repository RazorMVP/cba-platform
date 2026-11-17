# Expects dist/ to be pre-built before docker build
# CI: downloaded from the build job artifact; local: run npm run build first
FROM nginx:1.29-alpine
COPY dist /usr/share/nginx/html
# The image's entrypoint fills in ${API_UPSTREAM} from the environment at start (envsubst on
# /etc/nginx/templates/*.template → conf.d/). The filter limits it to that one variable.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
ENV API_UPSTREAM=http://backend:8080 \
    NGINX_ENVSUBST_FILTER=^API_UPSTREAM$
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
