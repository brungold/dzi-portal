(() => {
  const root = document.documentElement;
  const menuButton = document.querySelector('#menu-hamburger');
  const shell = document.querySelector('.app-shell');
  const sideNav = document.querySelector('.side-nav');
  const mobileQuery = window.matchMedia('(max-width: 780px)');
  const themeButton = document.querySelector('#theme-toggle');
  const search = document.querySelector('#module-search');
  const filters = [...document.querySelectorAll('.filter')];
  const tiles = [...document.querySelectorAll('.module-tile')];
  const empty = document.querySelector('#module-empty');
  let currentFilter = 'all';

  const savedTheme = localStorage.getItem('dzi-theme');
  if (savedTheme === 'dark') {
    root.dataset.theme = 'dark';
    themeButton?.setAttribute('aria-pressed', 'true');
    themeButton?.setAttribute('aria-label', 'Włącz tryb jasny');
  }

  themeButton?.addEventListener('click', () => {
    const dark = root.dataset.theme !== 'dark';
    root.dataset.theme = dark ? 'dark' : 'light';
    themeButton.setAttribute('aria-pressed', String(dark));
    themeButton.setAttribute('aria-label', dark ? 'Włącz tryb jasny' : 'Włącz tryb ciemny');
    localStorage.setItem('dzi-theme', root.dataset.theme);
  });

  if (menuButton && shell && sideNav) {
    const backdrop = document.createElement('div');
    backdrop.className = 'menu-backdrop';
    backdrop.setAttribute('aria-hidden', 'true');
    shell.appendChild(backdrop);

    const setMenuButton = expanded => {
      menuButton.setAttribute('aria-expanded', String(expanded));
      menuButton.setAttribute(
        'aria-label',
        expanded ? 'Ukryj menu boczne' : 'Pokaż menu boczne'
      );
    };

    const closeMobileMenu = () => {
      shell.classList.remove('mobile-menu-open');
      setMenuButton(false);
    };

    menuButton.addEventListener('click', () => {
      if (mobileQuery.matches) {
        const open = shell.classList.toggle('mobile-menu-open');
        setMenuButton(open);
      } else {
        const collapsed = shell.classList.toggle('menu-collapsed');
        localStorage.setItem('dzi-menu-collapsed', String(collapsed));
        setMenuButton(!collapsed);
      }
    });

    backdrop.addEventListener('click', closeMobileMenu);

    sideNav.querySelectorAll('a').forEach(link => {
      link.addEventListener('click', () => {
        if (mobileQuery.matches) closeMobileMenu();
      });
    });

    const applyDesktopMenuState = () => {
      const savedMenuState = localStorage.getItem('dzi-menu-collapsed');
      const collapsed = savedMenuState === null
        ? true
        : savedMenuState === 'true';

      shell.classList.toggle('menu-collapsed', collapsed);
      setMenuButton(!collapsed);
    };

    if (mobileQuery.matches) {
      shell.classList.remove('menu-collapsed');
      setMenuButton(false);
    } else {
      applyDesktopMenuState();
    }

    mobileQuery.addEventListener('change', event => {
      shell.classList.remove('mobile-menu-open');

      if (event.matches) {
        shell.classList.remove('menu-collapsed');
        setMenuButton(false);
      } else {
        applyDesktopMenuState();
      }
    });
  }

  function applyTileFilters() {
    if (!search || !empty) return;
    const query = search.value.trim().toLocaleLowerCase('pl');
    let visible = 0;

    tiles.forEach(tile => {
      const matchesKind = currentFilter === 'all' || tile.dataset.kind === currentFilter;
      const matchesText = !query || tile.textContent.toLocaleLowerCase('pl').includes(query);
      const show = matchesKind && matchesText;
      tile.hidden = !show;
      if (show) visible += 1;
    });

    empty.hidden = visible !== 0;
  }

  filters.forEach(button => {
    button.addEventListener('click', () => {
      currentFilter = button.dataset.filter;
      filters.forEach(item => {
        const active = item === button;
        item.classList.toggle('active', active);
        item.setAttribute('aria-pressed', String(active));
      });
      applyTileFilters();
    });
  });

  search?.addEventListener('input', applyTileFilters);

  document.querySelector('.filter-panel')?.addEventListener('submit', event => {
    event.preventDefault();
  });
})();
