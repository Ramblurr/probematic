
function Tooltip(ctx) {
  const content = ctx.getAttribute("data-tooltip");
  if( content ) {
    tippy(ctx, {
      content: content,
    })
  };
}

export function discoverTooltips() {
  document.querySelectorAll('.tooltip').forEach(element => Tooltip(element))
}


if(document.readyState !== 'loading') {
    discoverTooltips();
} else {
  document.addEventListener('DOMContentLoaded', function() {
    console.log("loaded")
    discoverTooltips();
  });
}
