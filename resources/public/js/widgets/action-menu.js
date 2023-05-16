import {$, enter, leave} from "../utils.js"
// based on https://sebastiandedeyne.com/javascript-framework-diet/enter-leave-transitions/
//
export function ActionMenu2(dropdownTrigger) {

    const selector = dropdownTrigger.getAttribute("data-action-menu2-trigger")
    const dropdownList = $(selector);
    const menu = dropdownList
    const button = dropdownTrigger
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
        onShown: (instance) => {
            if(htmx) {
                const root = htmx.find("[data-tippy-root]");
                if (root) {
                    htmx.process(root);
                    _hyperscript.processNode(root);
                }
            }
        },
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
      })
}

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
    const flyoutType =  selector.getAttribute("data-flyout-type") || "flyout-menu";
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
        enter(menu, flyoutType),
        enter(closeButtonContainer, 'flyout-closeButton')
    ])

    const close = async () => {
        await Promise.all([
            leave(backdrop, 'flyout-backdrop'),
            leave(menu, flyoutType),
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


export const ActionMenu2All = (target) => {
    (target || document).querySelectorAll('[data-action-menu2-trigger]').forEach(ActionMenu2)
}
