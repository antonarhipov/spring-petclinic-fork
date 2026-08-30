(function () {
  const statusEl = document.getElementById('status-text');
  if (!statusEl) {
    return;
  }
  const path = window.location.pathname;
  const match = path.match(/\/owner\/scheduling-requests\/(\d+)\/processing\/([0-9a-f-]+)/i);
  if (!match) {
    return;
  }
  const url = '/api/owner/scheduling-requests/' + match[1] + '/operations/' + match[2];
  function poll() {
    fetch(url, { headers: { 'Accept': 'application/json' }, cache: 'no-store' })
      .then(function (response) { return response.json(); })
      .then(function (body) {
        if (body.statusText) {
          statusEl.textContent = body.statusText;
        }
        if (body.nextUrl) {
          window.location.assign(body.nextUrl);
          return;
        }
        if (body.pollAfterMs) {
          window.setTimeout(poll, body.pollAfterMs);
        }
      });
  }
  poll();
})();
