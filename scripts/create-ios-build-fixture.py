"""Create a synthetic Firebase plist for unsigned compile checks, never for distribution."""
import plistlib
import sys
from pathlib import Path

destination = Path(sys.argv[1])
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_bytes(plistlib.dumps({
    "API_KEY": "compile-check-only-not-a-real-api-key",
    "GCM_SENDER_ID": "123456789012",
    "PLIST_VERSION": "1",
    "BUNDLE_ID": "com.ssutoday",
    "PROJECT_ID": "ssutoday-compile-check",
    "STORAGE_BUCKET": "ssutoday-compile-check.invalid",
    "IS_ADS_ENABLED": False,
    "IS_ANALYTICS_ENABLED": False,
    "IS_APPINVITE_ENABLED": False,
    "IS_GCM_ENABLED": True,
    "IS_SIGNIN_ENABLED": False,
    "GOOGLE_APP_ID": "1:123456789012:ios:0000000000000000000000",
}))
