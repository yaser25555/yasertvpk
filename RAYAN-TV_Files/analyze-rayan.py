
import zipfile
import re
import json
import sys

# Fix encoding for Windows
if sys.platform == "win32":
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

apk_path = "RAYAN-TV.apk"
print("=" * 60)
print(f"تحليل تطبيق: {apk_path}")
print("=" * 60)
print()

found_urls = set()
found_strings = []

# Read APK as zip
with zipfile.ZipFile(apk_path, 'r') as z:
    # Get all DEX files
    dex_files = [f for f in z.namelist() if f.endswith('.dex')]
    print(f"📦 عدد ملفات DEX: {len(dex_files)}")
    print()

    for dex_file in dex_files:
        print(f"🔍 تحليل {dex_file}...")
        data = z.read(dex_file)
        
        # Extract printable strings
        strings = re.findall(b'[\x20-\x7E]{4,}', data)
        
        for s in strings:
            try:
                decoded = s.decode('utf-8')
                found_strings.append(decoded)
                
                # Check for URLs
                if 'http://' in decoded or 'https://' in decoded:
                    found_urls.add(decoded)
            except:
                pass

print()
print("=" * 60)
print("🌐 الروابط التي تم العثور عليها:")
print("=" * 60)
for url in sorted(found_urls):
    print(f"   {url}")

print()
print("=" * 60)
print("🔍 البحث عن كلمات مفتاحية:")
print("=" * 60)

keywords = ['server', 'api', 'login', 'password', 'username', 'xtream', 'm3u', 'stream', 'rtmp']
found_keywords = []

for s in set(found_strings):
    for kw in keywords:
        if kw.lower() in s.lower():
            found_keywords.append(s)
            break

for kw_str in sorted(found_keywords):
    if len(kw_str) < 200:  # Filter out very long strings
        print(f"   {kw_str}")

# Save URLs to a file
with open("rayan_urls.txt", "w", encoding="utf-8") as f:
    for url in sorted(found_urls):
        f.write(url + "\n")

print()
print("=" * 60)
print("✅ تم الحفظ في: rayan_urls.txt")
print("=" * 60)

