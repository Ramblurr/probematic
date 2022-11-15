const isClosed = (tooltip) => tooltip.classList.contains("hidden");

function show(tooltip, popperInstance) {
  tooltip.classList.remove("hidden")

  // We need to tell Popper to update the tooltip position
  // after we show the tooltip, otherwise it will be incorrect
  popperInstance.update();
}

function hide(tooltip) {
  tooltip.classList.add("hidden")
}
