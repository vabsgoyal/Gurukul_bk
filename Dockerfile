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

# chromium + chromium-chromedriver: headless browser for the Jitsi moderator bot (see
# com.gurukul.calls.jitsi.JitsiBotService and docs/jitsi-bot-setup.md). Alpine ships a matched
# chromium/chromedriver pair, so no separate driver download/version-pinning is needed -
# JITSI_BOT_CHROME_BINARY/JITSI_BOT_CHROMEDRIVER_PATH below point at them. nss/freetype/harfbuzz/
# ttf-freefont are required for Chromium to actually render pages on Alpine at all.
RUN apk add --no-cache curl chromium chromium-chromedriver nss freetype harfbuzz ttf-freefont \
	&& addgroup -S gurukul && adduser -S gurukul -G gurukul
USER gurukul

ENV JITSI_BOT_CHROME_BINARY=/usr/bin/chromium-browser
ENV JITSI_BOT_CHROMEDRIVER_PATH=/usr/bin/chromedriver

COPY --from=build /app/target/gurukul-backend-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
