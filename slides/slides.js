(() => {
  const stage = document.getElementById('stage');
  const slides = [...document.querySelectorAll('.slide')];
  const progress = document.getElementById('progress-bar');
  const counter = document.getElementById('counter');
  const notesPanel = document.getElementById('notes-panel');
  const notesContent = document.getElementById('notes-content');
  const overview = document.getElementById('overview');
  const help = document.getElementById('help');
  let index = 0;

  function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  }

  function indexFromHash() {
    const match = window.location.hash.match(/^#(?:slide-)?(\d+)$/);
    return match ? clamp(Number(match[1]) - 1, 0, slides.length - 1) : 0;
  }

  function fitStage() {
    const scale = Math.min(window.innerWidth / 1280, window.innerHeight / 720);
    stage.style.transform = `translate(-50%, -50%) scale(${scale})`;
  }

  function refreshNotes() {
    const source = slides[index].querySelector('.speaker-notes');
    notesContent.innerHTML = source ? source.innerHTML : '<p>No notes for this slide.</p>';
  }

  function showSlide(nextIndex, updateHash = true) {
    index = clamp(nextIndex, 0, slides.length - 1);
    slides.forEach((slide, slideIndex) => {
      const active = slideIndex === index;
      slide.classList.toggle('active', active);
      slide.setAttribute('aria-hidden', active ? 'false' : 'true');
    });
    counter.textContent = `${index + 1} / ${slides.length}`;
    progress.style.width = `${((index + 1) / slides.length) * 100}%`;
    refreshNotes();
    document.title = `${String(index + 1).padStart(2, '0')} — ${slides[index].dataset.title}`;
    if (updateHash) history.replaceState(null, '', `#${index + 1}`);
    [...overview.children].forEach((card, cardIndex) => card.classList.toggle('current', cardIndex === index));
  }

  function next() { showSlide(index + 1); }
  function previous() { showSlide(index - 1); }

  function toggleNotes(force) {
    const open = typeof force === 'boolean' ? force : !notesPanel.classList.contains('open');
    notesPanel.classList.toggle('open', open);
    notesPanel.setAttribute('aria-hidden', open ? 'false' : 'true');
  }

  function toggleOverview(force) {
    const open = typeof force === 'boolean' ? force : !overview.classList.contains('open');
    overview.classList.toggle('open', open);
    overview.setAttribute('aria-hidden', open ? 'false' : 'true');
  }

  function toggleHelp(force) {
    const open = typeof force === 'boolean' ? force : !help.classList.contains('open');
    help.classList.toggle('open', open);
    help.setAttribute('aria-hidden', open ? 'false' : 'true');
  }

  function toggleFullscreen() {
    if (document.fullscreenElement) document.exitFullscreen();
    else document.documentElement.requestFullscreen?.();
  }

  function buildOverview() {
    slides.forEach((slide, slideIndex) => {
      const card = document.createElement('button');
      card.type = 'button';
      card.className = 'overview-card';
      card.innerHTML = `<span>${String(slideIndex + 1).padStart(2, '0')}</span><b>${slide.dataset.title}</b>`;
      card.addEventListener('click', () => {
        showSlide(slideIndex);
        toggleOverview(false);
      });
      overview.appendChild(card);
    });
  }

  document.getElementById('prev').addEventListener('click', previous);
  document.getElementById('next').addEventListener('click', next);
  document.getElementById('notes-button').addEventListener('click', () => toggleNotes());
  document.getElementById('close-notes').addEventListener('click', () => toggleNotes(false));
  document.getElementById('overview-button').addEventListener('click', () => toggleOverview());

  window.addEventListener('keydown', (event) => {
    if (event.altKey || event.ctrlKey || event.metaKey) return;
    const key = event.key;
    if (['ArrowRight', 'ArrowDown', 'PageDown', ' '].includes(key)) {
      event.preventDefault();
      next();
    } else if (['ArrowLeft', 'ArrowUp', 'PageUp'].includes(key)) {
      event.preventDefault();
      previous();
    } else if (key === 'Home') {
      event.preventDefault();
      showSlide(0);
    } else if (key === 'End') {
      event.preventDefault();
      showSlide(slides.length - 1);
    } else if (key.toLowerCase() === 'n') {
      toggleNotes();
    } else if (key.toLowerCase() === 'o') {
      toggleOverview();
    } else if (key.toLowerCase() === 'f') {
      toggleFullscreen();
    } else if (key === '?') {
      toggleHelp();
    } else if (key === 'Escape') {
      toggleNotes(false);
      toggleOverview(false);
      toggleHelp(false);
    }
  });

  window.addEventListener('hashchange', () => showSlide(indexFromHash(), false));
  window.addEventListener('resize', fitStage);
  window.addEventListener('orientationchange', fitStage);

  let touchStartX = null;
  window.addEventListener('touchstart', (event) => {
    touchStartX = event.changedTouches[0].clientX;
  }, { passive: true });
  window.addEventListener('touchend', (event) => {
    if (touchStartX === null) return;
    const distance = event.changedTouches[0].clientX - touchStartX;
    if (Math.abs(distance) > 60) distance < 0 ? next() : previous();
    touchStartX = null;
  }, { passive: true });

  buildOverview();
  fitStage();
  showSlide(indexFromHash(), false);
})();
