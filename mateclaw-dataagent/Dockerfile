# Runtime-only image.
#
# The Spring Boot fat jar (frontend SPA baked into BOOT-INF/classes/static)
# is built LOCALLY and expected at mateclaw-dataagent/target/*.jar. Docker
# does NOT compile anything anymore.
#
# Local build steps (run from the repo root):
#   1) cd mateclaw-dataagent-ui
#      npm ci
#      npx vite build --outDir ../mateclaw-dataagent/src/main/resources/static --emptyOutDir
#   2) Install the embedded runtime first (dataagent depends on mateclaw-sdk,
#      which depends on mateclaw-server; both must be in the local repo):
#      mvn -pl mateclaw-plugin-api,mateclaw-server install -Dmaven.test.skip=true
#      mvn -f mateclaw-sdk/pom.xml install -Dmaven.test.skip=true
#   3) cd mateclaw-dataagent
#      mvn package -Dmaven.test.skip=true
#   4) docker build -f mateclaw-dataagent/Dockerfile .
FROM eclipse-temurin:21-jdk-noble
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        file \
        fonts-noto-cjk \
        fonts-noto-color-emoji \
        git \
        poppler-utils \
        python3 \
        python3-pip \
        python3-venv \
        tesseract-ocr \
        tesseract-ocr-chi-sim \
        tzdata \
    && rm -rf /var/lib/apt/lists/*
# Runtime writes to RELATIVE paths under the working directory (WikiProperties
# uploadDir "./data/wiki-uploads", ChatUploadResolver "data/chat-uploads"),
# so /app itself — not just the home dir — must be owned by the app user.
# chown the directory BEFORE any large content lands in it: overlayfs copies
# a file up to the chown layer's diff, so `chown -R` after COPY/venv would
# duplicate the ~450 MB jar and the venv into an extra layer.
RUN groupadd -g 8001 app && useradd -r -u 8001 -g app -m app \
    && chown app:app /app
# App-owned virtualenv for the python_analysis tool. The skill doc promises
# stats (scipy), ML (scikit-learn), forecasting (statsmodels), plotting
# (matplotlib/seaborn/plotly) and Excel IO (openpyxl) as baseline
# capabilities; matplotlib renders CJK via the Noto fonts above.
# Created AS the app user so no chown pass is needed; a venv (instead of
# system site-packages) keeps the runtime `pip install -r requirements.txt`
# (LocalCodeExecutorService) writable and free of Ubuntu 24.04's PEP 668.
USER app
RUN python3 -m venv /app/python \
    && /app/python/bin/pip install --no-cache-dir \
        numpy pandas matplotlib seaborn plotly openpyxl \
        scipy scikit-learn statsmodels
# PYTHON_COMMAND / PIP_COMMAND bind to mateclaw.code-executor.* via the
# ${PYTHON_COMMAND:} placeholders in application.yml. PATH is also prepended so
# any hardcoded `python3` subprocess (e.g. DocumentExtractTool's pdfplumber/
# pypdf fallback) resolves to the venv rather than the bare system interpreter,
# which lacks the data-analysis packages.
ENV PATH=/app/python/bin:$PATH \
    PYTHON_COMMAND=/app/python/bin/python \
    PIP_COMMAND=/app/python/bin/pip \
    TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Shanghai -Dsun.jnu.encoding=UTF-8"
# --chown avoids the chown layer entirely; COPY is executed by the builder
# regardless of the current USER.
COPY --chown=8001:8001 mateclaw-dataagent/target/*.jar app.jar
USER app
EXPOSE 18089
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
