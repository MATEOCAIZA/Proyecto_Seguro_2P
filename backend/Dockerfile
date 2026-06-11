# ── Imagen base: JRE (no JDK) — solo para ejecutar, más liviana y segura ──────
FROM eclipse-temurin:21-jre-alpine

# Spring Boot escribe archivos temporales en /tmp
VOLUME /tmp

# Buena práctica de seguridad: no correr como root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copiar el .jar empaquetado por ./mvnw package
COPY target/*.jar app.jar

EXPOSE 8080

# -Djava.security.egd acelera el arranque de Spring en contenedores
# (evita bloqueos por falta de entropía en /dev/random)
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]

# ── Variables de entorno requeridas al correr el contenedor ───────────────────
# docker run -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/db \
#            -e SPRING_DATASOURCE_USERNAME=user \
#            -e SPRING_DATASOURCE_PASSWORD=pass \
#            -p 8080:8080 imagen:tag