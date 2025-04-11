FROM eclipse-temurin:21.0.6_7-jdk AS builder
WORKDIR /build
COPY . .
RUN chmod +x ./gradlew && ./gradlew clean jar --no-daemon

FROM eclipse-temurin:21.0.6_7-jdk
WORKDIR /app
COPY --from=builder /build/build/libs/*.jar app.jar

ENV JVM_OPTS="\
--add-opens=java.base/java.util=ALL-UNNAMED \
--add-opens=java.base/java.lang=ALL-UNNAMED \
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
-Xmx1g \
-Xms512m"

ENTRYPOINT java $JVM_OPTS -jar app.jar 