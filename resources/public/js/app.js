import {$, listen} from "./utils.js"
import {Dropdown, DropdownAll} from "./widgets/attendance-dropdown.js"
import {ActionMenu2All, ActionMenu,Flyout} from "./widgets/action-menu.js?v2"
import {discoverTooltips} from "./widgets/tooltip.js"

//// SETUP
htmx.onLoad(function(content) {
  htmx.config.defaultSettleDelay = 0;
  htmx.config.defaultSwapStyle = 'outerHTML';
})

function initWidgets(evt) {
  if (evt) {
    if(evt.target.querySelector(".dropdown")) {
      DropdownAll(evt.target)
    }
  } else {
    DropdownAll();
    listen('click', '[data-flyout-trigger]', Flyout);
  }

  ActionMenu2All()
  listen('click', '[data-action-menu-trigger]', ActionMenu);
  AutoSizeTextAreas();
  Dropzone.discover();
  discoverTooltips();
}

document.body.addEventListener('htmx:beforeSwap', function(evt) {
    if(evt.detail.xhr.status === 404){
      evt.detail.shouldSwap = true;
    } else if(evt.detail.xhr.status === 400){
      evt.detail.shouldSwap = true;
    } else if(evt.detail.xhr.status === 422){
        // allow 422 responses to swap as we are using this as a signal that
        // a form was submitted with bad data and want to rerender with the
        // errors
        //
        // set isError to false to avoid error logging in console
        evt.detail.shouldSwap = true;
        evt.detail.isError = false;
    }
});


document.body.addEventListener("htmx:beforeRequest", function(evt) {
  NProgress.start();
});

document.body.addEventListener("htmx:beforeSwap", function(evt) {
  NProgress.inc(0.9);
});

document.body.addEventListener("htmx:afterSettle", function(evt) {
  NProgress.done();
  setTimeout(() => NProgress.remove(), 1000)
  initWidgets(evt);
})


document.body.addEventListener("htmx:responseError", function(evt) {
  NProgress.done();
  NProgress.remove();
});

document.body.addEventListener("htmx:sendError", function(evt) {
  NProgress.done();
  NProgress.remove();
});

document.addEventListener('DOMContentLoaded', function() {
  initWidgets();
});

document.addEventListener('newDropdown', function(evt) {
  Dropdown(document.querySelector(evt.detail.value))
});

function checkboxLimitReached(event) {
  const checkedChecks = document.querySelectorAll("[data-checkbox-limit]:checked");
  if(checkedChecks.length == 0 ) return false;
  const maximum = parseInt(checkedChecks[0].getAttribute("data-maximum"));
  if (checkedChecks.length >= maximum + 1) {
    console.log("max reached")
    return true;
  }
  return false;
}

window.checkboxLimitReached = checkboxLimitReached;

function pageDirtyHandler(event) {
    event.returnValue = "Are you sure you want to leave? You have unsaved changes.";
}

function setPageDirty() {
  document.addEventListener('beforeunload', pageDirtyHandler);
}

function unsetPageDirty() {
  document.removeEventListener('beforeunload', pageDirtyHandler);
}

function AutoSizeTextAreas() {

  function onTextAreaInput() {
    this.style.height = 0;
    this.style.height = (this.scrollHeight) + "px";
  }

  const txs = document.querySelectorAll("textarea[data-auto-size]");
  for (let i = 0; i < txs.length; i++) {
    txs[i].setAttribute("style", "height:" + (txs[i].scrollHeight) + "px;overflow-y:hidden;");
    txs[i].addEventListener("input", onTextAreaInput, false);
  }
}


document.addEventListener('setPageDirty', function(evt) {
  setPageDirty();
});
