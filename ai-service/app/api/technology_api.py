from fastapi import APIRouter

from app.services.technology_service import get_technologies

router = APIRouter()


@router.get("/technologies")
def technologies():
    return get_technologies()
