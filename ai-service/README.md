# AI Service OCR Cache Notes

## Rebuild the image

```bash
docker compose build --no-cache api
docker compose up -d
```

## Clear OCR cache

Remove the named volumes if you want PaddleOCR to download models again:

```bash
docker compose down -v
```

You can also remove only the OCR cache volume:

```bash
docker volume ls
docker volume rm <your_project_name>_paddleocr_cache
```

## Verify cache reuse

1. Start the container once and let PaddleOCR download its models.
2. Restart the container with `docker compose restart api`.
3. Check the logs.
4. The second run should reuse the cached PaddleOCR/Paddle models instead of downloading them again.

## Notes

- The service starts with `uvicorn main:app --host 0.0.0.0 --port 8000`.
- OCR cache is persisted through the `paddleocr_cache` and `python_cache` volumes.
- The import directory is mounted at `/app/uploads`.
