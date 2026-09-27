#!/usr/bin/env python3
"""Smoke-test one ingress shared by multiple BeeHome app replicas."""

import json
import ssl
import sys
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: verify_ingress.py https://host:port /path/to/tls.crt")
    base_url, certificate = sys.argv[1:]
    context = ssl.create_default_context(cafile=certificate)

    def send(attempt: int) -> tuple[int, bool]:
        request = Request(
            base_url.rstrip("/") + "/api/auth/login",
            data=b"{}",
            headers={
                "Content-Type": "application/json",
                "X-BeeHome-Client-IP": f"198.51.100.{attempt + 1}",
            },
            method="POST",
        )
        try:
            with urlopen(request, context=context, timeout=15) as response:
                return response.status, False
        except HTTPError as response:
            if response.code == 429:
                body = json.load(response)
                assert body.get("code") == "RATE_LIMITED", body
                assert response.headers.get("Retry-After") == "60", response.headers
            return response.code, response.headers.get("X-BeeHome-Limit") == "ingress"

    with ThreadPoolExecutor(max_workers=50) as executor:
        responses = list(executor.map(send, range(50)))
    counts = Counter(status for status, _ in responses)
    edge_rejections = sum(edge for _, edge in responses)
    print(f"Authentication POST statuses: {dict(sorted(counts.items()))}; ingress 429: {edge_rejections}")
    assert counts[400] > 0, "No request reached the application"
    assert edge_rejections > 0, "The coordinated ingress limit did not reject a burst"
    assert not (set(counts) - {400, 429}), "Unexpected status from ingress or app"


if __name__ == "__main__":
    main()
