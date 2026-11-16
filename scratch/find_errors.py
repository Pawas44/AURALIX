import urllib.request
url = 'https://razorpay.me/@pawas?amount=100'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req) as resp:
        html = resp.read().decode('utf-8')
        print(html[:2000])
except urllib.error.HTTPError as e:
    print('HTTP error:', e.code, e.read().decode('utf-8'))
except Exception as e:
    print('Error:', e)
