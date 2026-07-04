# Build stage
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl \
	&& addgroup -S gurukul && adduser -S gurukul -G gurukul
USER gurukul

COPY --from=build /app/target/gurukul-backend-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
