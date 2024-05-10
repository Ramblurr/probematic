import confetti from "./canvas-confetti@1.9.3.js";

function cleanup(c) {
  if (c) {
    c.clearCanvas();
    c.destroyCanvas();
  }
}

var count = 200;
var defaults = {
  origin: { y: 0.3 },
  useWorker: true,
  disableForReducedMotion: true,
  resize: true,
};

function fire(particleRatio, opts) {
  confetti({
    ...defaults,
    ...opts,
    particleCount: Math.floor(count * particleRatio),
  });
}
function celebrate() {
  navigator.vibrate =
    navigator.vibrate ||
    navigator.webkitVibrate ||
    navigator.mozVibrate ||
    navigator.msVibrate;

  if (navigator.vibrate) {
    navigator.vibrate(100);
  }

  fire(0.25, {
    spread: 26,
    startVelocity: 55,
  });
  fire(0.2, {
    spread: 60,
  });
  fire(0.35, {
    spread: 100,
    decay: 0.91,
    scalar: 0.8,
  });
  fire(0.1, {
    spread: 120,
    startVelocity: 25,
    decay: 0.92,
    scalar: 1.2,
  });
  fire(0.1, {
    spread: 120,
    startVelocity: 45,
  });
}

function discover() {
  // find all elements with the data-celebrate attribute
  document.querySelectorAll("[data-celebrate]").forEach((el) => {
    el.removeAttribute("data-celebrate");
    celebrate();
  });
}

document.body.addEventListener("htmx:afterSettle", function (evt) {
  discover();
});
document.addEventListener("DOMContentLoaded", function () {
  discover();
});

discover();
window.celebrate = celebrate;

export default celebrate;
