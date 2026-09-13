# Dashboard external prerequisite checks
.PHONY: dashboard-prerequisites-local dashboard-prerequisites-simulation dashboard-prerequisites-external dashboard-dataagent-test dashboard-runner-test dashboard-ui-test dashboard-ui-build dashboard-verify-local

dashboard-prerequisites-local:
	./scripts/verify-dashboard-external-prerequisites.sh --local

dashboard-prerequisites-simulation:
	./scripts/verify-dashboard-external-prerequisites.sh --simulation

dashboard-prerequisites-external:
	./scripts/verify-dashboard-external-prerequisites.sh --external

# Run the DataAgent suite from a Maven container against Docker Desktop. The
# host override is required for Testcontainers' dynamically published ports;
# Ryuk is disabled because the Maven container cannot accept its callback on
# the Desktop bridge network. Containers are still removed by Testcontainers'
# JVM shutdown hooks and by the normal Docker cleanup commands.
dashboard-dataagent-test:
	docker run --rm \
		-e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
		-e TESTCONTAINERS_RYUK_DISABLED=true \
		-v "$(CURDIR):/workspace" \
		-v "$(HOME)/.m2:/root/.m2" \
		-v /var/run/docker.sock:/var/run/docker.sock \
		-w /workspace maven:3.9-eclipse-temurin-21 \
		mvn -o -f mateclaw-dataagent/pom.xml test -q

# Run the Runner suite from its own uv-managed environment. Keeping the
# working directory and pytest path scoped to this project avoids collecting
# the sibling Python client tests with incompatible dependencies.
dashboard-runner-test:
	@test -x mateclaw-python-runner/.venv/bin/pytest || { echo "缺少 Runner 虚拟环境，请先在 mateclaw-python-runner 执行 uv sync --dev" >&2; exit 2; }
	cd mateclaw-python-runner && .venv/bin/pytest -q

# Run the UI unit suite and production type/build gate with the repository's
# installed Node dependencies.
dashboard-ui-test:
	npm --prefix mateclaw-dataagent-ui run test -- --run

dashboard-ui-build:
	npm --prefix mateclaw-dataagent-ui run build

# Full local implementation gate. It uses only the disposable simulation
# stack and never claims the external Aloudata authorization gate.
dashboard-verify-local: dashboard-prerequisites-simulation dashboard-dataagent-test dashboard-runner-test dashboard-ui-test dashboard-ui-build
	bash scripts/verify-dashboard-design.sh

# Docker buildx builder setup
.PHONY: builder-create builder-rm builder-inspect

BUILDER_NAME := multi-builder

builder-create:
	docker buildx create \
	  --driver docker-container \
	  --driver-opt network=host \
	  --use \
	  --config ~/.docker/buildkitd.toml \
	  --name $(BUILDER_NAME)

builder-rm:
	docker buildx rm $(BUILDER_NAME)

builder-inspect:
	docker buildx inspect --bootstrap

# Docker build targets
# Convention: -sg suffix = SGCCR (singapore), -gz suffix = CCR (guangzhou)

METACLAW_SERVER_IMAGE_TAG := 1.4.15-SNAPSHOT
MATECLAW_SERVER_SG_IMAGE := sgccr.ccs.tencentyun.com/connor-ai-lab/mateclaw-server:$(METACLAW_SERVER_IMAGE_TAG)
MATECLAW_SERVER_GZ_IMAGE := ccr.ccs.tencentyun.com/connor-ai-lab/mateclaw-server:$(METACLAW_SERVER_IMAGE_TAG)
# MATECLAW_SERVER_IMAGE := connor-mateclaw-registry.zeabur.app/mateclaw/mateclaw-server:$(IMAGE_TAG)
# MATECLAW_SERVER_TENCENT_IMAGE := ccr.ccs.tencentyun.com/connor-ai-lab/mateclaw/mateclaw-server:$(IMAGE_TAG)

SEARXNG_IMAGE_TAG := 1.0.3-SNAPSHOT
SEARXNG_SG_IMAGE := sgccr.ccs.tencentyun.com/connor-ai-lab/mateclaw-searxng:$(SEARXNG_IMAGE_TAG)
SEARXNG_GZ_IMAGE := ccr.ccs.tencentyun.com/connor-ai-lab/mateclaw-searxng:$(SEARXNG_IMAGE_TAG)

MIHOMO_IMAGE_TAG := 1.0.2-SNAPSHOT
MIHOMO_GZ_IMAGE := ccr.ccs.tencentyun.com/connor-ai-lab/mihomo-client:$(MIHOMO_IMAGE_TAG)

# mateclaw-server
build-sg:
	docker buildx build \
	  --platform linux/amd64 \
	  --no-cache \
	  -f mateclaw-server/Dockerfile \
	  --build-arg MAVEN_FLAGS="-Paliyun-first" \
	  -t $(MATECLAW_SERVER_SG_IMAGE) \
	  --push \
	  --progress=plain .

build-gz:
	docker buildx build \
	  --platform linux/amd64 \
	  --no-cache \
	  -f mateclaw-server/Dockerfile \
	  --build-arg MAVEN_FLAGS="-Paliyun-first" \
	  -t $(MATECLAW_SERVER_GZ_IMAGE) \
	  --push \
	  --progress=plain .

# searxng
pull-searxng:
	docker pull --platform linux/amd64 searxng/searxng:latest

build-searxng-sg:
	docker buildx build \
	  --platform linux/amd64 \
	  -f docker/searxng/Dockerfile \
	  -t $(SEARXNG_SG_IMAGE) \
	  --push \
	  --progress=plain .

build-searxng-gz:
	docker buildx build \
	  --platform linux/amd64 \
	  -f docker/searxng/Dockerfile \
	  -t $(SEARXNG_GZ_IMAGE) \
	  --push \
	  --progress=plain .

# mihomo
build-mihomo-gz:
	docker buildx build \
	  --platform linux/amd64 \
	  -f docker/mihomo/Dockerfile \
	  -t $(MIHOMO_GZ_IMAGE) \
	  --push \
	  --progress=plain .
