FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 UV_COMPILE_BYTECODE=1 PYTHONPATH=/app/src
WORKDIR /app
RUN useradd --create-home --uid 10001 runner
COPY --from=ghcr.io/astral-sh/uv:0.5.21 /uv /uvx /bin/
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev
COPY src ./src
RUN chown -R runner:runner /app
USER runner
EXPOSE 8080
CMD ["/app/.venv/bin/uvicorn", "runner.app:app", "--host", "0.0.0.0", "--port", "8080"]
