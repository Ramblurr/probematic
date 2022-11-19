import {$, listen} from "./utils.js"
import {Dropdown, DropdownAll} from "./widgets/attendance-dropdown.js"
import {ActionMenu,Flyout} from "./widgets/action-menu.js"

//// SETUP
htmx.onLoad(function(content) {
  htmx.config.defaultSettleDelay = 0;
  htmx.config.defaultSwapStyle = 'outerHTML';
})

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
  if(evt.target.querySelector(".dropdown")) {
    DropdownAll(evt.target)
  }
  if(evt.target.querySelector(".sortable")) {
    initSortable(evt.target);
  }
})

const initSortable = (target) => {
  console.log("init sortable")
    var sortables = target.querySelectorAll(".sortable");
    for (var i = 0; i < sortables.length; i++) {
      var sortable = sortables[i];
      new Sortable(sortable, {
          animation: 150,
        ghostClass: 'bg-blue-300',
        handle: '.drag-handle'
      });
    }
}


document.body.addEventListener("htmx:responseError", function(evt) {
  NProgress.done();
  NProgress.remove();
});

document.body.addEventListener("htmx:sendError", function(evt) {
  NProgress.done();
  NProgress.remove();
});

document.addEventListener('DOMContentLoaded', function() {
  if (document.querySelector('.dropdown')) {
    DropdownAll();
  }

  listen('click', '[data-action-menu-trigger]', ActionMenu);
  listen('click', '[data-flyout-trigger]', Flyout);
});

document.addEventListener('newDropdown', function(evt) {
  Dropdown(document.querySelector(evt.detail.value))
});
