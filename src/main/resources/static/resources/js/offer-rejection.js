(function () {
  const form = document.querySelector("[data-offer-reject]");
  if (!form) {
    return;
  }
  const step1 = document.getElementById("reject-step-1");
  const step2 = document.getElementById("reject-step-2");
  const startButton = document.getElementById("reject-start-button");
  const cancelButton = document.getElementById("reject-cancel-button");
  if (!step1 || !step2 || !startButton) {
    return;
  }
  startButton.addEventListener("click", function () {
    step1.hidden = true;
    step2.hidden = false;
  });
  if (cancelButton) {
    cancelButton.addEventListener("click", function () {
      step2.hidden = true;
      step1.hidden = false;
    });
  }
})();
