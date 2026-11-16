import urllib.request, json, sys

run_id = sys.argv[1] if len(sys.argv) > 1 else None
if not run_id:
    url = 'https://api.github.com/repos/Pawas44/AURALIX/actions/runs?per_page=1'
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        latest = data.get('workflow_runs', [])[0]
        run_id = latest.get('id')
        print(f"Latest Run #{latest.get('run_number')} (ID: {run_id}) - Status: {latest.get('status')}, Conclusion: {latest.get('conclusion')}")

url = f'https://api.github.com/repos/Pawas44/AURALIX/actions/runs/{run_id}/jobs'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        for j in data.get('jobs', []):
            name = j.get('name')
            st = j.get('status')
            conc = j.get('conclusion')
            print(f'Job: {name} | Status: {st} | Conclusion: {conc}')
except Exception as e:
    print('Error:', e)
