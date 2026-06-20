from __future__ import annotations

import json
import logging
import os
from urllib.parse import urlparse, urlunparse
from urllib import error, request


logger = logging.getLogger(__name__)


class CallbackClient:
    def post_json(self, url: str, payload: dict[str, object], timeout_seconds: float = 30.0) -> bool:
        resolved_url = self._resolve_url(url)
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req = request.Request(
            resolved_url,
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with request.urlopen(req, timeout=timeout_seconds) as response:
                response.read()
            return True
        except error.HTTPError as exc:
            logger.warning("Callback HTTP error %s calling %s", exc.code, resolved_url)
            return False
        except Exception as exc:
            logger.warning("Callback request failed for %s: %s", resolved_url, exc)
            return False

    def _resolve_url(self, url: str) -> str:
        override_host = os.getenv("CALLBACK_HOST_OVERRIDE", "").strip()
        parsed = urlparse(url)
        if override_host:
            return urlunparse(parsed._replace(netloc=override_host))

        if parsed.hostname in {"localhost", "127.0.0.1"}:
            host = "host.docker.internal"
            netloc = host
            if parsed.port:
                netloc = f"{host}:{parsed.port}"
            return urlunparse(parsed._replace(netloc=netloc))

        return url
