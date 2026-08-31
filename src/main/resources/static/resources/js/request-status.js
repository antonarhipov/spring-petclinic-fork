/**
 * Request status polling script for Owner scheduling requests.
 * Uses non-interactive GET /owner/requests/{requestId}/status.
 */
(function() {
  'use strict';

  function startRequestStatusPoller(requestId, statusUrl) {
    let pollerTimeout = null;
    let isStopped = false;

    function poll() {
      if (isStopped) return;

      fetch(statusUrl, {
        method: 'GET',
        headers: {
          'Accept': 'application/json'
        }
      })
      .then(response => {
        if (!response.ok) {
          if (response.status === 401) {
            // Session expired
            window.location.href = '/login';
            return;
          }
          throw new Error('Status response not ok: ' + response.status);
        }
        return response.json();
      })
      .then(data => {
        if (!data) return;

        const stateBadge = document.getElementById('request-state-badge');
        if (stateBadge && data.displayState) {
          stateBadge.textContent = data.displayState;
        }

        // If the state has transitioned to a actionable or terminal state, redirect to canonicalUrl or reload
        if (data.displayState === 'REVIEW_INTERPRETATION' || data.displayState === 'APPOINTMENT_OFFERED' || data.displayState === 'CONFIRMED') {
          if (data.canonicalUrl && window.location.pathname !== data.canonicalUrl) {
            window.location.href = data.canonicalUrl;
            return;
          }
        }

        if (data.terminal) {
          isStopped = true;
          const spinner = document.getElementById('status-spinner-container');
          if (spinner) spinner.classList.add('d-none');
          const content = document.getElementById('status-content-container');
          if (content) content.classList.remove('d-none');
          return;
        }

        const interval = data.pollAfterMillis || 3000;
        pollerTimeout = setTimeout(poll, interval);
      })
      .catch(err => {
        console.warn('Polling error:', err);
        // Retry with backoff
        pollerTimeout = setTimeout(poll, 5000);
      });
    }

    // Start initial poll after 1.5s
    pollerTimeout = setTimeout(poll, 1500);

    window.addEventListener('beforeunload', () => {
      isStopped = true;
      if (pollerTimeout) clearTimeout(pollerTimeout);
    });
  }

  window.startRequestStatusPoller = startRequestStatusPoller;
})();
