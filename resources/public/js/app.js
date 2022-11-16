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
        // alert the user when a 404 occurs (maybe use a nicer mechanism than alert())
        alert("Error: Could Not Find Resource TODO make this nicer");
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
  if (document.querySelector('.dropdown')) {
    DropdownAll();
  }

  listen('click', '[data-action-menu-trigger]', ActionMenu);
  listen('click', '[data-flyout-trigger]', Flyout);
});

document.addEventListener('newDropdown', function(evt) {
  Dropdown(document.querySelector(evt.detail.value))
});
