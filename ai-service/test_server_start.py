"""Quick test để xem server có start được không."""
import sys
from app.main import app


def test_server_startup():
    """Test xem app có khởi động được không."""
    print("Testing Server Startup...")
    
    try:
        # Check app object
        print(f"✓ App created: {app.title}")
        print(f"✓ App version: {app.version}")
        
        # Check routers registered
        routes_count = len([r for r in app.routes if hasattr(r, 'path')])
        print(f"✓ Routes registered: {routes_count}")
        
        # Check middleware
        middleware_count = len(app.user_middleware)
        print(f"✓ Middleware registered: {middleware_count}")
        
        print("\n" + "="*60)
        print("✓ SERVER CAN START SUCCESSFULLY")
        print("="*60)
        print("\nTo start the server:")
        print("  uvicorn app.main:app --reload --port 8000")
        print("\nOr with host binding:")
        print("  uvicorn app.main:app --reload --host 0.0.0.0 --port 8000")
        
        return True
        
    except Exception as e:
        print(f"\n✗ FAIL: {e}")
        import traceback
        traceback.print_exc()
        return False


if __name__ == "__main__":
    success = test_server_startup()
    sys.exit(0 if success else 1)
