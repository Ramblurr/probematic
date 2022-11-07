function show(tooltip, popperInstance) {
  tooltip.classList.remove("hidden")

  // We need to tell Popper to update the tooltip position
  // after we show the tooltip, otherwise it will be incorrect
  popperInstance.update();
}

function hide(tooltip) {
  tooltip.classList.add("hidden")
}

const isClosed = (tooltip) => tooltip.classList.contains("hidden");

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


document.body.addEventListener("htmx:responseError", function(evt) {
  NProgress.done();
  NProgress.remove();
});

document.body.addEventListener("htmx:sendError", function(evt) {
  NProgress.done();
  NProgress.remove();
});

const ClickHandler = (root, instance) => (e) => {
  e.preventDefault();
  const value = e.target.getAttribute("data-value");
  const svg = e.target.querySelector('svg').cloneNode(true)
  console.log("selected", value)
  root.querySelector(".item-icon").innerHTML = svg.outerHTML;
  root.querySelector(".item-input").value = value;
  instance.hide();
}

const Dropdown = (root) => {
  const menu = root.querySelector('.dropdown-menu-container');
  const button = root.querySelector('.dropdown-button');
  let tippyInstance;
  let onClick;
  tippy(button, {
    content: menu.innerHTML ,
    allowHTML: true,
    interactive: true,
    trigger: 'click',
    placement: 'bottom',
    offset: [0, 0],
    arrow: false,
    theme: "translucent",
    popperOptions: {
      strategy: 'fixed',
      modifiers: [
        {
          name: 'flip',
          options: {
            fallbackPlacements: ["top", "left"],
          },
        },
        {
          name: 'preventOverflow',
          options: {
            altAxis: true,
            tether: false,
          },
        },
      ],
    },
    onCreate(instance) {
      tippyInstance = instance;
      onClick = ClickHandler(root, instance);
    },

    onShow(instance) {
      instance.popper.querySelectorAll('a').forEach(el =>
        el.addEventListener('click', onClick)
      )
    },

    onHide(instance) {
      instance.popper.querySelectorAll('a').forEach(el =>
        el.removeEventListener('click', onClick)
      )
    },

    onDestroy(instance) {
      tippyInstance = null;
      onClick = null;
    },

    onHidden(instance) {
    }
  })

}

const DropdownAll = () => {
  document.querySelectorAll('.dropdown').forEach(Dropdown)
}

document.addEventListener('DOMContentLoaded', function() {
  DropdownAll();
});
