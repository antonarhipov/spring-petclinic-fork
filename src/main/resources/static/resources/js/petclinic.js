(function () {
  "use strict";

  var status = document.getElementById("global-submit-status");
  var statusMessage = document.getElementById("global-submit-message");

  function restoreForms() {
    document.querySelectorAll("form[aria-busy='true']").forEach(function (form) {
      form.removeAttribute("aria-busy");
      form.querySelectorAll("[data-submit-disabled='true']").forEach(function (control) {
        control.disabled = false;
        delete control.dataset.submitDisabled;
      });
      form.querySelectorAll("button[data-original-html]").forEach(function (button) {
        button.innerHTML = button.dataset.originalHtml;
        delete button.dataset.originalHtml;
      });
    });
    if (status) {
      status.hidden = true;
    }
  }

  document.addEventListener("submit", function (event) {
    var form = event.target;
    if (!(form instanceof HTMLFormElement) || form.getAttribute("aria-busy") === "true") {
      if (form instanceof HTMLFormElement) {
        event.preventDefault();
      }
      return;
    }

    var submitter = event.submitter || form.querySelector("button[type='submit'], input[type='submit'], button:not([type])");
    var message = form.dataset.loadingMessage || (submitter && submitter.dataset.loadingMessage)
      || (statusMessage && statusMessage.textContent) || "Working...";

    form.setAttribute("aria-busy", "true");
    form.querySelectorAll("button[type='submit'], input[type='submit'], button:not([type])").forEach(function (control) {
      if (!control.disabled) {
        control.dataset.submitDisabled = "true";
        control.disabled = true;
      }
    });

    if (submitter instanceof HTMLButtonElement) {
      submitter.dataset.originalHtml = submitter.innerHTML;
      submitter.innerHTML = '<span class="submit-spinner" aria-hidden="true"></span>' + message;
    }

    if (status && statusMessage) {
      statusMessage.textContent = message;
      status.hidden = false;
    }
  });

  window.addEventListener("pageshow", restoreForms);
})();
