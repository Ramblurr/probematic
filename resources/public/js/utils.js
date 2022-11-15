export function $(selector, scope = document) {
  return scope.querySelector(selector);
}

export function $$(selector, scope = document) {
  return Array.from(scope.querySelectorAll(selector));
}

export function listen(type, selector, callback) {
  document.addEventListener(type, event => {
    const target = event.target.closest(selector);

    if (target) {
      callback(event, target);
    }
  });
}

export async function enter(element, transition) {
  element.classList.remove('hidden');

  element.classList.add(`${transition}-enter`);
  element.classList.add(`${transition}-enter-start`);

  await nextFrame();

  element.classList.remove(`${transition}-enter-start`);
  element.classList.add(`${transition}-enter-end`);

  await afterTransition(element);

  element.classList.remove(`${transition}-enter-end`);
  element.classList.remove(`${transition}-enter`);
}

export async function leave(element, transition) {
  element.classList.add(`${transition}-leave`);
  element.classList.add(`${transition}-leave-start`);

  await nextFrame();

  element.classList.remove(`${transition}-leave-start`);
  element.classList.add(`${transition}-leave-end`);

  await afterTransition(element);

  element.classList.remove(`${transition}-leave-end`);
  element.classList.remove(`${transition}-leave`);

  element.classList.add('hidden');
}

function nextFrame() {
  return new Promise(resolve => {
    requestAnimationFrame(() => {
      requestAnimationFrame(resolve);
    });
  });
}

function afterTransition(element) {
  return new Promise(resolve => {
    const duration = Number(
      getComputedStyle(element)
        .transitionDuration
        .replace('s', '')
    ) * 1000;

    setTimeout(() => {
      resolve();
    }, duration);
  });
}
