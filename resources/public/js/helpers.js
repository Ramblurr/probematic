htmx.onLoad(function(content) {
  htmx.config.defaultSettleDelay = 0;
  htmx.config.defaultSwapStyle = 'outerHTML';
})

document.body.addEventListener("htmx:beforeRequest", function(evt) {
  NProgress.start();
});

document.body.addEventListener("htmx:beforeSwap", function(evt) {
  NProgress.inc(0.9);
});

document.body.addEventListener("htmx:afterSettle", function(evt) {
  NProgress.done();
  setTimeout(() => NProgress.remove(), 1000)
});
