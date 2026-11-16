import urllib.request, json
url = 'https://api.github.com/repos/Pawas44/AURALIX/releases/latest'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        print('Tag:', data.get('tag_name'))
        print('Name:', data.get('name'))
        for a in data.get('assets', []):
            print(f"Asset: {a.get('name')} -> {a.get('browser_download_url')}")
except Exception as e:
    print('Error:', e)
