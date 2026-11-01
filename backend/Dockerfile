# ---------- Stage 1: build the jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only the build definition first, then download dependencies.
# This layer is cached until pom.xml changes, so code edits don't re-download everything.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline

# Tests are skipped here: they need Docker (Testcontainers), and CI runs them in its own job.
COPY src/ src/
RUN ./mvnw -q package -DskipTests

# ---------- Stage 2: run the jar ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as an unprivileged user, not root.
RUN useradd --system --uid 1001 jfj
COPY --from=build /app/target/*.jar app.jar
USER jfj

# Profile and memory settings come from the host's environment (e.g. Render), not the image.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
