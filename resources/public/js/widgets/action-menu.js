import {$, enter, leave} from "../utils.js"
// based on https://sebastiandedeyne.com/javascript-framework-diet/enter-leave-transitions/

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


export async function Flyout(event, flyoutTrigger) {
    const selector = $(flyoutTrigger.getAttribute("data-flyout-trigger"))
    const backdrop = $("[data-flyout-backdrop]", selector)
    const menu = $("[data-flyout-menu]", selector)
    const closeButtonContainer = $("[data-flyout-close-button]", selector)
    const closeButton = $("button", closeButtonContainer)

    if (!selector.classList.contains('hidden')) {
        return;
    }
    selector.classList.remove('hidden');
    await Promise.all([
        enter(backdrop, 'flyout-backdrop'),
        enter(menu, 'flyout-menu'),
        enter(closeButtonContainer, 'flyout-closeButton')
    ])

    const close = async () => {
        await Promise.all([
            leave(backdrop, 'flyout-backdrop'),
            leave(menu, 'flyout-menu'),
            leave(closeButtonContainer, 'flyout-closeButton'),
        ])
        selector.classList.add('hidden');

        window.removeEventListener('click', handleClick);
        closeButton.removeEventListener('click', handleClickButton);
    }

    function handleClick(event) {
        if (!menu.contains(event.target)) {
            close()
        }
    }
    function handleClickButton(event) {
        close()
    }

    window.requestAnimationFrame(() => {
        window.addEventListener('click', handleClick);
        closeButton.addEventListener('click', handleClickButton);
    });
}
