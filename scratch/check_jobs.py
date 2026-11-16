import urllib.request, json, time, sys

run_id = sys.argv[1] if len(sys.argv) > 1 else '43775978900'
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
