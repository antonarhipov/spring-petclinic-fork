(function () {
  const body = document.body;
  const requestId = body.getAttribute('data-request-id');
  const currentState = body.getAttribute('data-current-state') || 'INTERPRETING';

  if (!requestId) {
    return;
  }

  const pollIntervalMs = 1500;

  function checkStatus() {
    fetch('/my/requests/' + encodeURIComponent(requestId) + '/status', {
      headers: { 'Accept': 'application/json' }
    })
      .then(function (response) {
        if (!response.ok) {
          return null;
        }
        return response.json();
      })
      .then(function (data) {
        if (data && data.state && data.state !== currentState) {
          window.location.reload();
        } else {
          setTimeout(checkStatus, pollIntervalMs);
        }
      })
      .catch(function () {
        setTimeout(checkStatus, pollIntervalMs);
      });
  }

  setTimeout(checkStatus, pollIntervalMs);
})();
