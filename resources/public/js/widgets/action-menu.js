import {$, enter, leave} from "../utils.js"

export function ActionMenu(event, dropdownTrigger) {
  const selector = dropdownTrigger.getAttribute("data-action-menu-trigger")

  const dropdownList = $(selector);

  console.log(dropdownTrigger)
  console.log(selector)
  console.log(dropdownList)

  if (!dropdownList.classList.contains('hidden')) {
    return;
  }

  enter(dropdownList, 'fade');

  function handleClick(event) {
    if (!dropdownList.contains(event.target)) {
      leave(dropdownList, 'fade');

      window.removeEventListener('click', handleClick);
    }
  }

  window.requestAnimationFrame(() => {
    window.addEventListener('click', handleClick);
  });
}
