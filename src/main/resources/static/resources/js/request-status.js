/**
 * Request status polling script for Owner scheduling requests.
 * Uses non-interactive GET /owner/requests/{requestId}/status.
 */
(function() {
  'use strict';

  function startRequestStatusPoller(requestId, statusUrl) {
    let pollerTimeout = null;
    let isStopped = false;
    let consecutiveErrors = 0;

    function removePollingErrorNotice() {
      const existingNotice = document.getElementById('polling-error-notice');
      if (existingNotice) {
        existingNotice.remove();
      }
    }

    function showPollingErrorNotice(message) {
      removePollingErrorNotice();
      const container = document.getElementById('status-content-container') || document.getElementById('status-spinner-container');
      if (container && container.parentNode) {
        const alert = document.createElement('div');
        alert.id = 'polling-error-notice';
        alert.className = 'alert alert-warning alert-dismissible fade show mt-3';
        alert.role = 'alert';
        alert.innerHTML = '<span class="fa fa-info-circle"></span> ' + message;
        container.parentNode.insertBefore(alert, container);
      }
    }

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
            // Session expired or unauthenticated
            isStopped = true;
            window.location.href = '/login';
            return null;
          }
          if (response.status === 404) {
            isStopped = true;
            showPollingErrorNotice('Request details are no longer available.');
            return null;
          }
          throw new Error('Status response not ok: ' + response.status);
        }
        consecutiveErrors = 0;
        removePollingErrorNotice();
        return response.json();
      })
      .then(data => {
        if (!data || isStopped) return;

        const stateBadge = document.getElementById('request-state-badge');
        const previousState = stateBadge ? stateBadge.textContent.trim() : '';
        if (stateBadge && data.displayState) {
          stateBadge.textContent = data.displayState;
        }

        // If the server provides a canonical URL that differs from current path, navigate to it
        if (data.canonicalUrl && window.location.pathname !== data.canonicalUrl) {
          window.location.href = data.canonicalUrl;
          return;
        }

        const isProcessing = data.displayState === 'INTERPRETING_REQUEST' || data.displayState === 'FINDING_APPOINTMENT';
        const spinner = document.getElementById('status-spinner-container');
        const content = document.getElementById('status-content-container');

        if (!isProcessing) {
          if (spinner) spinner.classList.add('d-none');
          if (content) content.classList.remove('d-none');

          // If previous state was processing and now it transitioned on the same page, reload to refresh rendered model
          if (previousState === 'INTERPRETING_REQUEST' || previousState === 'FINDING_APPOINTMENT') {
            window.location.reload();
            return;
          }
        } else {
          if (spinner) spinner.classList.remove('d-none');
          if (content) content.classList.add('d-none');
        }

        if (data.terminal) {
          isStopped = true;
          if (spinner) spinner.classList.add('d-none');
          if (content) content.classList.remove('d-none');
          return;
        }

        const interval = data.pollAfterMillis || 2000;
        pollerTimeout = setTimeout(poll, interval);
      })
      .catch(err => {
        console.warn('Polling error:', err);
        consecutiveErrors++;
        if (consecutiveErrors >= 3) {
          showPollingErrorNotice('Unable to reach server. Retrying status update...');
        }
        // Retry with backoff up to 10s
        const backoff = Math.min(10000, 3000 * consecutiveErrors);
        pollerTimeout = setTimeout(poll, backoff);
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
