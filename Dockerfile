# ===== Stage 1: Build =====
# Uses the full JDK to compile and package the application
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy Maven wrapper and pom.xml first (for Docker layer caching)
# If pom.xml hasn't changed, Docker reuses the cached dependency layer
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Now copy source code and build
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ===== Stage 2: Runtime =====
# Uses JRE-only image (smaller: ~200MB vs ~400MB for full JDK)
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy only the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Run as non-root user for security
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

EXPOSE 8080

# Use exec form for proper signal handling (graceful shutdown)
ENTRYPOINT ["java", "-jar", "app.jar"]
