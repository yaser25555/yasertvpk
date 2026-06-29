
import requests
import json
import sys

# Fix encoding for Windows
if sys.platform == "win32":
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

url = "https://raw.githubusercontent.com/used4/online/refs/heads/main/Alameer-altqny.json"
print(f"Downloading from: {url}")
print()

try:
    response = requests.get(url)
    response.raise_for_status()
    
    data = response.json()
    
    print("=" * 60)
    print("Configuration file content:")
    print("=" * 60)
    print(json.dumps(data, indent=2, ensure_ascii=False))
    print()
    
    # Save the file
    with open("rayan_config.json", "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    
    print("Saved to: rayan_config.json")
    print("Done!")
    
except Exception as e:
    print(f"Error: {e}")

