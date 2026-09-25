/* =========================================================================
   ORIGIN — www.olona.no/origin
   No dependencies, no build step. Everything here is progressive enhancement:
   the page reads fine with JS disabled, this only adds the gallery, the
   lightbox, the mobile menu, sprite icons and scroll reveals.
   ========================================================================= */
(function () {
  'use strict';

  var ATLAS = 'assets/sprites/origin-game.png';
  var ATLAS_SIZE = 2048; // assets/sprites/origin-game.png is 2048x2048

  /* Crop rects in the game atlas, verified against the PNG's alpha bounds.
     Tiles ship as their own small PNGs and are referenced by <img> instead. */
  var SPRITES = {
    coin:    { x: 1038, y: 872,  w: 128, h: 128 },
    heart:   { x: 1550, y: 1909, w: 128, h: 118 },
    bag:     { x: 1832, y: 1661, w: 88,  h: 96  },
    chest:   { x: 1556, y: 1486, w: 108, h: 86  },
    crystal: { x:  776, y: 873,  w: 128, h: 128 },
    slash:   { x:   68, y:  306, w: 256, h: 256 },
    dagger:  { x:  260, y:  273, w: 64,  h: 17  },
    key:     { x:    2, y:   34, w: 128, h: 64  },
    /* Cropped tight to the artwork so the sprite fills its frame. */
    goblin:   { x: 1844, y: 1032, w: 68,  h: 55 },
    mosquito: { x: 1836, y: 1939, w: 74,  h: 60 },
    spider:   { x: 1455, y: 1426, w: 60,  h: 49 }
  };

  /* The game's "Knight" enemy is a goblin sprite at 1.5x scale
     (EnemyType.KNIGHT -> SpriteConstants.EnemyKnightSprite = "goblin"). */
  SPRITES.knight = SPRITES.goblin;

  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  /* ---------------------------------------------------------------- sprites */
  /* Each [data-sprite] is a clipping box; we drop a scaled copy of the atlas
     region inside it, centred. Integer-agnostic so it fits any box size. */
  function mountSprites() {
    $$('[data-sprite]').forEach(function (box) {
      var spec = SPRITES[box.getAttribute('data-sprite')];
      if (!spec) return;

      var bw = box.clientWidth || parseFloat(getComputedStyle(box).width);
      var bh = box.clientHeight || parseFloat(getComputedStyle(box).height);
      if (!bw || !bh) return;

      var scale = Math.min(bw / spec.w, bh / spec.h);
      var img = document.createElement('i');
      img.className = 'sprite';
      img.style.width = spec.w + 'px';
      img.style.height = spec.h + 'px';
      img.style.backgroundImage = 'url("' + ATLAS + '")';
      img.style.backgroundSize = (ATLAS_SIZE * scale) + 'px ' + (ATLAS_SIZE * scale) + 'px';
      img.style.backgroundPosition = (-spec.x * scale) + 'px ' + (-spec.y * scale) + 'px';
      img.style.transform = 'translate(-50%, -50%) scale(' + scale + ')';
      // Nearest-neighbour only when we scale up; shrinking pixel art with
      // nearest drops whole rows of pixels and the icon turns to noise.
      img.style.imageRendering = scale < 1 ? 'auto' : 'pixelated';
      box.appendChild(img);
    });
  }

  /* ---------------------------------------------------------------- gallery */
  /* Screenshot slots. Drop a PNG with the same name into web/img/shots/ and it
     appears here automatically — press P in game to produce one. Missing files
     fall back to a labelled placeholder instead of a broken image. */
  var SHOTS = [
    { file: 'origin_tutorial_level_2_000.png', caption: 'Tutorial - Level 2', device: 'desktop' },
    { file: 'origin_tutorial_level_2_001.png', caption: 'Tutorial - Level 2', device: 'desktop' },
    { file: 'origin_tutorial_level_2_002.png', caption: 'Tutorial - Level 2', device: 'desktop' },
    { file: 'origin_tutorial_level_3_003.png', caption: 'Tutorial - Level 3', device: 'desktop' },
    { file: 'origin_tutorial_level_3_004.png', caption: 'Tutorial - Level 3', device: 'desktop' },
    { file: 'origin_menu.png',          caption: 'Main menu',             device: 'desktop' },
    { file: 'origin_phone-1.png',       caption: 'On a phone',            device: 'phone' },
    { file: 'origin_phone-2.png',       caption: 'Touch controls',        device: 'phone' },
    { file: 'origin_phone-3.png',       caption: 'The run, in one hand',  device: 'phone' }
  ];

  var gallery = { items: [], index: 0 };

  function buildGallery() {
    var grid = $('#shot-grid');
    if (!grid) return;

    SHOTS.forEach(function (shot) {
      var li = document.createElement('li');
      li.className = 'shot shot-device shot-device--' + shot.device;

      var device = document.createElement('div');
      device.className = 'shot-device shot-device--' + shot.device;

      var frame = document.createElement('button');
      frame.type = 'button';
      frame.className = 'shot-frame';

      var img = document.createElement('img');
      img.src = 'img/shots/' + shot.file;
      img.alt = shot.caption;
      img.loading = 'lazy';
      img.decoding = 'async';
      img.addEventListener('error', function () { markMissing(li, frame, img, shot); });

      frame.appendChild(img);
      device.appendChild(frame);
      li.appendChild(device);

      var cap = document.createElement('p');
      cap.className = 'shot-caption';
      cap.innerHTML = '<b>' + shot.caption + '</b><span class="shot-file">' + shot.file + '</span>';
      li.appendChild(cap);

      grid.appendChild(li);
      gallery.items.push({ li: li, frame: frame, img: img, shot: shot });
      frame.addEventListener('click', function () {
        if (li.classList.contains('is-missing')) return;
        openLightbox(gallery.items.indexOf(gallery.items.filter(function (i) { return i.li === li; })[0]));
      });
    });
  }

  function markMissing(li, frame, img, shot) {
    li.classList.add('is-missing');
    img.remove();
    var note = document.createElement('span');
    note.className = 'shot-missing';
    note.innerHTML = '<b>Screenshot pending</b>Press <kbd>P</kbd> in game,<br>then save it as<br><code>web/img/shots/' + shot.file + '</code>';
    frame.appendChild(note);
  }

  /* -------------------------------------------------------------- lightbox */
  var lightbox, lbImg, lbCaption, lastFocus;

  function openLightbox(index) {
    if (!lightbox || !gallery.items.length) return;
    var item = gallery.items[index];
    if (!item) return;
    gallery.index = index;
    lbImg.src = item.img.src;
    lbImg.alt = item.shot.caption;
    lbCaption.textContent = item.shot.caption;
    lightbox.hidden = false;
    document.body.style.overflow = 'hidden';
    lastFocus = document.activeElement;
    $('#lightbox-close').focus();
  }

  function closeLightbox() {
    if (!lightbox || lightbox.hidden) return;
    lightbox.hidden = true;
    document.body.style.overflow = '';
    if (lastFocus) lastFocus.focus();
  }

  function stepLightbox(delta) {
    if (!gallery.items.length) return;
    openLightbox((gallery.index + delta + gallery.items.length) % gallery.items.length);
  }

  function initLightbox() {
    lightbox = $('#lightbox');
    lbImg = $('#lightbox-img');
    lbCaption = $('#lightbox-caption');
    if (!lightbox) return;

    $('#lightbox-close').addEventListener('click', closeLightbox);
    $('#lightbox-prev').addEventListener('click', function () { stepLightbox(-1); });
    $('#lightbox-next').addEventListener('click', function () { stepLightbox(1); });
    lightbox.addEventListener('click', function (e) {
      if (e.target === lightbox) closeLightbox();
    });
    document.addEventListener('keydown', function (e) {
      if (lightbox.hidden) return;
      if (e.key === 'Escape') closeLightbox();
      else if (e.key === 'ArrowLeft') stepLightbox(-1);
      else if (e.key === 'ArrowRight') stepLightbox(1);
    });
  }

  /* ------------------------------------------------------------- site chrome */
  function initHeader() {
    var header = $('.site-header');
    var toggle = $('#nav-toggle');
    var nav = $('#nav');

    var onScroll = function () {
      header.classList.toggle('is-stuck', window.scrollY > 8);
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });

    if (toggle && nav) {
      toggle.addEventListener('click', function () {
        var open = nav.classList.toggle('is-open');
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        toggle.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
      });
      nav.addEventListener('click', function (e) {
        if (e.target.tagName === 'A') {
          nav.classList.remove('is-open');
          toggle.setAttribute('aria-expanded', 'false');
        }
      });
    }
  }

  function initReveal() {
    var items = $$('.reveal');
    if (!items.length) return;

    var show = function (el) { el.classList.add('is-in'); };

    if (!('IntersectionObserver' in window) ||
        window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      items.forEach(show);
      return;
    }

    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          show(entry.target);
          io.unobserve(entry.target);
        }
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.08 });
    items.forEach(function (el) { io.observe(el); });

    // Safety net: the observer only ever sees a position the browser rendered,
    // so an anchor jump, a restored scroll position or a fast flick can leave an
    // element sitting in view with opacity 0. Anything at or above the fold
    // always gets shown, whatever happened to the observer.
    var pass = function () {
      var limit = window.innerHeight * 0.94;
      items.forEach(function (el) {
        if (!el.classList.contains('is-in') && el.getBoundingClientRect().top < limit) {
          show(el);
          io.unobserve(el);
        }
      });
    };
    window.addEventListener('scroll', pass, { passive: true });
    window.addEventListener('resize', pass);
    window.addEventListener('load', pass);
    pass();
  }

  /* ---------------------------------------------------------------- trailer */
  function initTrailer() {
    var btn = $('#trailer-play');
    var video = $('#trailer-video');
    var poster = $('.trailer-poster');
    var note = $('#trailer-note');
    if (!btn || !video) return;

    btn.addEventListener('click', function () {
      if (!video.getAttribute('src')) {
        // TODO(trailer): set the real file name here when the trailer exists.
        video.src = 'media/origin-trailer.mp4';
      }
      video.hidden = false;
      if (poster) poster.hidden = true;
      if (note) note.hidden = true;
      video.play().catch(function () {
        if (note) {
          note.hidden = false;
          note.textContent = 'That trailer file is not here yet — drop web/media/origin-trailer.mp4 in and reload.';
        }
      });
    });
    video.addEventListener('error', function () {
      video.hidden = true;
      if (poster) poster.hidden = false;
      if (note) {
        note.hidden = false;
        note.textContent = 'That trailer file is not here yet — drop web/media/origin-trailer.mp4 in and reload.';
      }
    });
  }

  /* ------------------------------------------------------ small site extras */
  function initStores() {
    $$('[data-comingsoon]').forEach(function (a) {
      a.addEventListener('click', function (e) {
        e.preventDefault();
        var label = $('.store-name', a);
        var original = label ? label.textContent : '';
        if (label) label.textContent = 'Not out yet';
        setTimeout(function () { if (label) label.textContent = original; }, 1400);
      });
    });
  }

  function initSignup() {
    var form = $('#signup');
    var status = $('#signup-status');
    var input = $('#email');
    if (!form) return;

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var value = (input.value || '').trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
        status.textContent = 'That address does not look right.';
        return;
      }
      // TODO(list): no provider is wired up yet — hand off to a real service
      // or a mailing list. Until then this opens a prefilled mail draft so the
      // form is never a dead end.
      status.textContent = 'Opening a mail draft so nothing gets lost…';
      window.location.href = 'mailto:origin@olona.no?subject=' +
        encodeURIComponent('ORIGIN launch notification') +
        '&body=' + encodeURIComponent('Add ' + value + ' to the ORIGIN launch list.');
    });
  }

  function initYear() {
    var y = $('#year');
    if (y) y.textContent = new Date().getFullYear();
  }

  /* ------------------------------------------------------------------- boot */
  function boot() {
    // Tells the head-script failsafe that reveal handling is ours now, so it
    // does not un-hide the page if this file is slow to arrive.
    document.documentElement.setAttribute('data-reveal-ready', '');

    initHeader();
    initLightbox();
    buildGallery();
    mountSprites();
    initReveal();
    initTrailer();
    initStores();
    initSignup();
    initYear();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
