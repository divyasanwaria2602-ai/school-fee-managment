FROM postgres:16-alpine

# Copy SQL init scripts from project into image so host file sharing isn't required
COPY src/main/resources/db/migration /docker-entrypoint-initdb.d

# Ensure scripts are readable
RUN chmod -R 755 /docker-entrypoint-initdb.d
