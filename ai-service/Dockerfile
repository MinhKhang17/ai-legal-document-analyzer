FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app
ENV PADDLEOCR_HOME=/root/.paddleocr
ENV XDG_CACHE_HOME=/root/.cache
ENV PATH="/usr/lib/ccache:${PATH}"

ARG PADDLEPADDLE_VERSION=3.3.0

RUN apt-get update \
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
        poppler-utils \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .

RUN pip install --upgrade pip \
    && pip install --no-cache-dir paddlepaddle==${PADDLEPADDLE_VERSION} -i https://www.paddlepaddle.org.cn/packages/stable/cpu/ \
    && pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
