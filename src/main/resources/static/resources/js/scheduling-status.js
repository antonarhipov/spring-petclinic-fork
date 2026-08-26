/*
 * Live in-place status updates for the owner scheduling flow.
 *
 * While a scheduling request is being processed asynchronously (the AI is
 * interpreting the request, or the constraint solver is finding a slot) this
 * script polls the server-rendered status fragment and swaps it into the page in
 * place, so the two-step stepper advances without a jarring full-page reload.
 *
 * The endpoint returns plain server-rendered HTML (no JSON/REST API), keeping
 * the application aligned with the "server-rendered Thymeleaf only" rule. The
 * swapped-in markup contains normal forms and links (including CSRF tokens), so
 * they keep working after a swap with no client-side rewiring.
 *
 * With JavaScript disabled this script never runs; the <noscript> meta-refresh
 * fallback in status.html keeps the flow advancing.
 */
(function () {
  'use strict';

  var CONTAINER_ID = 'status-content';
  var POLL_INTERVAL_MS = 1500;
  var MAX_BACKOFF_MS = 12000;

  // States where async work is still in progress and the page should keep
  // polling. DRAFT is included because a request created (or re-submitted) with
  // AI consent is handed off to the async interpreter immediately; that
  // background work runs inside a single transaction that spans the AI call, so
  // until it commits its outcome pollers still observe the last committed state
  // (DRAFT) rather than INTERPRETING. Without polling on DRAFT the page would
  // stay on the initial spinner forever and never pick up the final state. Every
  // other state is an action or terminal state that stops polling
  // (AWAITING_CONFIRMATION, SLOT_HELD, CONFIRMED, STAFF_QUEUED, CANCELLED,
  // EXPIRED, REJECTED).
  var BUSY_STATES = ['DRAFT', 'INTERPRETING', 'SUGGESTING'];

  function isBusy(state) {
    return BUSY_STATES.indexOf(state) !== -1;
  }

  function currentState() {
    var el = document.getElementById(CONTAINER_ID);
    return el ? el.getAttribute('data-request-state') : null;
  }

  function fragmentUrl() {
    // The full status page lives at /scheduling/requests/{id}; the fragment is a
    // sibling endpoint. Deriving from the current path keeps any deployment
    // context path intact without embedding it in the script.
    var path = window.location.pathname.replace(/\/+$/, '');
    return path + '/status-fragment';
  }

  function init() {
    if (!document.getElementById(CONTAINER_ID)) {
      return;
    }
    if (!isBusy(currentState())) {
      // Action or terminal state on load: nothing to poll for.
      return;
    }

    var url = fragmentUrl();
    var backoff = 0;
    var timer = null;
    var stopped = false;

    function stop() {
      stopped = true;
      if (timer !== null) {
        window.clearTimeout(timer);
        timer = null;
      }
    }

    function schedule(delay) {
      if (stopped) {
        return;
      }
      timer = window.setTimeout(poll, delay);
    }

    function swap(html) {
      var el = document.getElementById(CONTAINER_ID);
      if (el) {
        // Replace the whole container: the fragment response *is* the
        // #status-content element, so its data-request-state and the stepper
        // markup come along and the stepper advances automatically.
        el.outerHTML = html;
      }
    }

    function poll() {
      if (stopped) {
        return;
      }
      window
        .fetch(url, {
          headers: { 'X-Requested-With': 'XMLHttpRequest' },
          credentials: 'same-origin',
          cache: 'no-store'
        })
        .then(function (response) {
          if (!response.ok) {
            throw new Error('Unexpected status ' + response.status);
          }
          return response.text();
        })
        .then(function (html) {
          backoff = 0;
          swap(html);
          if (isBusy(currentState())) {
            schedule(POLL_INTERVAL_MS);
          }
          else {
            // Reached an action or terminal state; the swapped-in content is the
            // final screen for this step, so stop polling.
            stop();
          }
        })
        .catch(function () {
          // Transient network/server error: keep the current screen and retry
          // with an exponential backoff, but only while still busy.
          if (!isBusy(currentState())) {
            stop();
            return;
          }
          backoff = Math.min(backoff ? backoff * 2 : POLL_INTERVAL_MS * 2, MAX_BACKOFF_MS);
          schedule(backoff);
        });
    }

    schedule(POLL_INTERVAL_MS);
  }

  if (!window.fetch) {
    // No fetch support: leave the current screen in place; the user can reload.
    return;
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  }
  else {
    init();
  }
})();
