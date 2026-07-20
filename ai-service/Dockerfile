# syntax=docker/dockerfile:1.7
FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app
ENV PADDLEOCR_HOME=/root/.paddleocr
ENV XDG_CACHE_HOME=/root/.cache
ENV PATH="/usr/lib/ccache:${PATH}"

ARG PADDLEPADDLE_VERSION=3.3.0

RUN --mount=type=cache,id=lexiguard-ai-apt-lists,target=/var/lib/apt/lists,sharing=locked \
    --mount=type=cache,id=lexiguard-ai-apt-cache,target=/var/cache/apt,sharing=locked \
    apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        curl \
        antiword \
        ccache \
        fonts-dejavu-core \
        libgl1 \
        libglib2.0-0 \
        libsm6 \
        libxext6 \
        libxrender1 \
        poppler-utils

COPY requirements.txt .

RUN --mount=type=cache,id=lexiguard-ai-pip-cache,target=/root/.cache/pip,sharing=locked \
    pip install --upgrade pip \
    && pip install paddlepaddle==${PADDLEPADDLE_VERSION} \
    && pip install torch --index-url https://download.pytorch.org/whl/cpu \
    && pip install -r requirements.txt

COPY . .

EXPOSE 8000

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
