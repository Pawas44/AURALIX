import urllib.request, json
url = 'https://api.github.com/repos/Pawas44/AURALIX/actions/runs?per_page=3'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        for r in data.get('workflow_runs', []):
            print(f"Run #{r.get('run_number')} (ID: {r.get('id')}) - status: {r.get('status')}, conclusion: {r.get('conclusion')} | {r.get('head_commit', {}).get('message', '')[:60]}")
except Exception as e:
    print('Error:', e)
