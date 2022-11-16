const ClickHandler = (root, instance) => (e) => {
  e.preventDefault();
  const value = e.target.getAttribute("data-value");
  const svg = e.target.querySelector('svg').cloneNode(true)
  root.querySelector(".item-icon").innerHTML = svg.outerHTML;
  root.querySelector(".item-input").value = value;
  const newSvg = root.querySelector(".item-icon").querySelector("svg")
  newSvg.classList.remove("w-5")
  newSvg.classList.remove("h-5")
  newSvg.classList.add("w-3")
  newSvg.classList.add("h-3")
  const form = root.closest('form');
  htmx.trigger(form, "planChanged")
  instance.hide();
}

export const Dropdown = (root) => {
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

export const DropdownAll = (target) => {
  if (target) {
    target.querySelectorAll('.dropdown').forEach(Dropdown)
  } else {
    document.querySelectorAll('.dropdown').forEach(Dropdown)
  }
}
