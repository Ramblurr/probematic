function Tooltip(ctx) {
  const content = ctx.getAttribute("data-tooltip");
  if (content) {
    tippy(ctx, {
      content: content,
      touch: true,
    });
  }
}

export function DiscoverTooltips(target) {
  (target || document)
    .querySelectorAll(".tooltip")
    .forEach((element) => Tooltip(element));
}

if (document.readyState !== "loading") {
  DiscoverTooltips();
} else {
  document.addEventListener("DOMContentLoaded", function () {
    console.log("loaded");
    DiscoverTooltips();
  });
}
