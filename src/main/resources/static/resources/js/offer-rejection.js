(function () {
  const form = document.querySelector("[data-offer-reject]");
  if (!form) {
    return;
  }
  form.addEventListener("submit", function (event) {
    if (!window.confirm(form.getAttribute("data-confirm") || "Decline this time?")) {
      event.preventDefault();
    }
  });
})();
