(function () {
  var container = document.getElementById("hold-status");
  if (!container) {
    return;
  }
  var expiresAt = new Date(container.getAttribute("data-expires-at")).getTime();
  var clinicZone = container.getAttribute("data-clinic-zone");
  var exactEl = document.getElementById("hold-expiry-exact");
  var countdownEl = document.getElementById("hold-countdown");
  var warningEl = document.getElementById("hold-warning");
  var expiredEl = document.getElementById("hold-expired-message");
  var acceptForm = document.getElementById("accept-form");
  var acceptButton = document.getElementById("accept-button");
  var nextActions = document.getElementById("hold-next-actions");
  var WARNING_THRESHOLD_MS = 2 * 60 * 1000;

  if (exactEl && clinicZone) {
    try {
      exactEl.textContent = new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "long",
        timeZone: clinicZone
      }).format(new Date(expiresAt));
    }
    catch (ignored) {
      // keep the server-rendered fallback text
    }
  }

  function pad(value) {
    return value < 10 ? "0" + value : String(value);
  }

  function expire() {
    if (countdownEl) {
      countdownEl.textContent = "00:00";
    }
    if (warningEl) {
      warningEl.hidden = true;
    }
    if (expiredEl) {
      expiredEl.hidden = false;
    }
    if (acceptButton) {
      acceptButton.disabled = true;
    }
    if (acceptForm) {
      acceptForm.hidden = true;
    }
    if (nextActions) {
      nextActions.hidden = false;
    }
  }

  function tick() {
    var remainingMs = expiresAt - Date.now();
    if (remainingMs <= 0) {
      window.clearInterval(timer);
      expire();
      return;
    }
    var totalSeconds = Math.floor(remainingMs / 1000);
    if (countdownEl) {
      countdownEl.textContent = pad(Math.floor(totalSeconds / 60)) + ":" + pad(totalSeconds % 60);
    }
    if (warningEl) {
      warningEl.hidden = remainingMs > WARNING_THRESHOLD_MS;
    }
  }

  var timer = window.setInterval(tick, 1000);
  tick();
})();
