"""API routes for AI service."""
import logging
from fastapi import APIRouter
# Temporarily disable rag_service due to model incompatibility
# from app.services.rag_service import rag_service

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "service": "ai-service"}


# RAG endpoints are now handled by rag_api.py
# This file is kept for backward compatibility with health check
