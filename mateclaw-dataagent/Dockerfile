FROM node:22-alpine AS frontend-builder
WORKDIR /frontend
COPY mateclaw-dataagent-ui/package.json mateclaw-dataagent-ui/package-lock.json ./
RUN npm ci
COPY mateclaw-dataagent-ui/ ./
RUN NODE_OPTIONS=--max-old-space-size=4096 npx vite build --outDir /static --emptyOutDir

FROM maven:3.9-eclipse-temurin-21 AS builder
ARG MAVEN_FLAGS=""
COPY mateclaw-server/settings.xml /root/.m2/settings.xml
WORKDIR /build
COPY pom.xml ./pom.xml
COPY mateclaw-plugin-api/pom.xml mateclaw-plugin-api/pom.xml
COPY mateclaw-server/pom.xml mateclaw-server/pom.xml
COPY mateclaw-plugin-sample/pom.xml mateclaw-plugin-sample/pom.xml
RUN mvn -pl mateclaw-plugin-api,mateclaw-server dependency:go-offline -q ${MAVEN_FLAGS}
COPY mateclaw-plugin-api/src mateclaw-plugin-api/src
COPY mateclaw-server/src mateclaw-server/src
RUN mvn -pl mateclaw-plugin-api,mateclaw-server install -Dmaven.test.skip=true -q ${MAVEN_FLAGS}
COPY mateclaw-sdk/pom.xml mateclaw-sdk/pom.xml
COPY mateclaw-dataagent/pom.xml mateclaw-dataagent/pom.xml
RUN mvn -pl mateclaw-dataagent dependency:go-offline -q ${MAVEN_FLAGS}
COPY mateclaw-sdk/src mateclaw-sdk/src
COPY mateclaw-dataagent/src mateclaw-dataagent/src
COPY --from=frontend-builder /static mateclaw-dataagent/src/main/resources/static
RUN mvn -pl mateclaw-sdk install -Dmaven.test.skip=true -q ${MAVEN_FLAGS} \
    && mvn -pl mateclaw-dataagent package -Dmaven.test.skip=true -q ${MAVEN_FLAGS}

FROM eclipse-temurin:21-jdk-noble
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        fonts-noto-cjk \
        fonts-noto-color-emoji \
        python3 \
        python3-pip \
        python3-venv \
        tzdata \
    && rm -rf /var/lib/apt/lists/*
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Shanghai -Dsun.jnu.encoding=UTF-8"
COPY --from=builder /build/mateclaw-dataagent/target/*.jar app.jar
RUN groupadd -g 8001 app && useradd -r -u 8001 -g app -m app && chown -R app:app /app
USER app
EXPOSE 18089
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
