(() => {
  'use strict';

  const state = {
    data: null,
    version: null,
    view: 'attrs',
    q: '',
    filters: {
      classes: new Set(),
      types: new Set(),
      flags: new Set(),
      deprecated: false,
      immutable: false,
    },
  };

  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => Array.from(document.querySelectorAll(sel));

  // Data is loaded via <script> tags (see index.html) so the bundle works from file://.
  function boot() {
    if (!window.ATTRS_DATA) {
      document.body.innerHTML =
        '<p style="padding:40px;font-family:system-ui">Failed to load attrs.js — the bundle looks incomplete.</p>';
      return;
    }
    state.data = window.ATTRS_DATA;
    state.version = window.VERSION_DATA || {};
    renderVersion();
    renderFacets();
    bindEvents();
    syncFacetVisibility();
    syncFromHash();
    render();
  }

  function syncFacetVisibility() {
    const el = $('#facet-attrs');
    if (!el) return;
    el.classList.toggle('hidden-section', state.view !== 'attrs');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }

  function renderVersion() {
    const v = state.version || {};
    const parts = [];
    if (v.version) parts.push(v.version);
    if (v.commit && v.commit !== 'unknown') parts.push(v.commit.slice(0, 7));
    $('#version-label').textContent = parts.length ? parts.join(' · ') : '';
  }

  function renderFacets() {
    const types = new Set(state.data.attrs.map((a) => a.type).filter(Boolean));
    const flagsInUse = new Set();
    state.data.attrs.forEach((a) => a.flags.forEach((f) => flagsInUse.add(f)));

    mountCheckboxList('#facet-class', state.data.classes, state.filters.classes);
    mountCheckboxList('#facet-type', [...types].sort(), state.filters.types);
    mountCheckboxList('#facet-flag', [...flagsInUse].sort(), state.filters.flags);
  }

  function mountCheckboxList(sel, values, targetSet) {
    const host = $(sel);
    host.innerHTML = '';
    values.forEach((v) => {
      const label = document.createElement('label');
      const cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.value = v;
      cb.addEventListener('change', () => {
        if (cb.checked) targetSet.add(v);
        else targetSet.delete(v);
        render();
      });
      const text = document.createElement('span');
      text.textContent = v;
      label.appendChild(cb);
      label.appendChild(text);
      host.appendChild(label);
    });
  }

  function bindEvents() {
    $('#search').addEventListener('input', (e) => {
      state.q = e.target.value.trim().toLowerCase();
      render();
    });
    $('#facet-deprecated').addEventListener('change', (e) => {
      state.filters.deprecated = e.target.checked;
      render();
    });
    $('#facet-immutable').addEventListener('change', (e) => {
      state.filters.immutable = e.target.checked;
      render();
    });
    $$('.tab').forEach((btn) => {
      btn.addEventListener('click', () => {
        $$('.tab').forEach((t) => t.classList.remove('active'));
        btn.classList.add('active');
        state.view = btn.dataset.view;
        syncFacetVisibility();
        render();
      });
    });
    $('#reset').addEventListener('click', () => {
      state.q = '';
      $('#search').value = '';
      state.filters.classes.clear();
      state.filters.types.clear();
      state.filters.flags.clear();
      state.filters.deprecated = false;
      state.filters.immutable = false;
      $('#facet-deprecated').checked = false;
      $('#facet-immutable').checked = false;
      $$('.sidebar input[type=checkbox]').forEach((cb) => {
        if (cb.id !== 'facet-deprecated' && cb.id !== 'facet-immutable') cb.checked = false;
      });
      render();
    });
    $('#detail').addEventListener('click', (e) => {
      if (e.target.id === 'detail' || e.target.classList.contains('close')) closeDetail();
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') closeDetail();
      if (e.key === '/' && e.target !== $('#search') && !e.metaKey && !e.ctrlKey) {
        e.preventDefault();
        $('#search').focus();
        $('#search').select();
      }
    });
    window.addEventListener('hashchange', syncFromHash);
  }

  function syncFromHash() {
    const m = location.hash.match(/^#(attr|oc)=([^&]+)/);
    if (!m) {
      closeDetail();
      return;
    }
    const [, kind, rawName] = m;
    const name = decodeURIComponent(rawName);
    const list = kind === 'attr' ? state.data.attrs : state.data.objectClasses;
    const found = list.find((x) => x.name === name);
    if (found) openDetail(kind, found);
  }

  function filtered() {
    if (state.view === 'ocs') {
      return state.data.objectClasses.filter((oc) => {
        if (!state.q) return true;
        return (
          oc.name.toLowerCase().includes(state.q) ||
          (oc.description || '').toLowerCase().includes(state.q)
        );
      });
    }
    const f = state.filters;
    return state.data.attrs.filter((a) => {
      if (state.q) {
        const hay =
          a.name.toLowerCase() +
          '\n' + (a.description || '').toLowerCase() +
          '\n' + a.flags.join(' ').toLowerCase();
        if (!hay.includes(state.q)) return false;
      }
      if (f.deprecated && !a.deprecated) return false;
      if (f.immutable && !a.immutable) return false;
      if (f.types.size && !f.types.has(a.type)) return false;
      if (f.flags.size && !a.flags.some((x) => f.flags.has(x))) return false;
      if (f.classes.size) {
        const classes = new Set([...(a.requiredIn || []), ...(a.optionalIn || [])]);
        if (![...f.classes].some((c) => classes.has(c))) return false;
      }
      return true;
    });
  }

  function render() {
    const items = filtered();
    $('#count').textContent =
      items.length + ' ' + (state.view === 'ocs' ? 'object classes' : 'attributes');

    const ul = $('#results');
    ul.innerHTML = '';
    if (!items.length) {
      const empty = document.createElement('li');
      empty.className = 'empty';
      empty.textContent = 'No matches.';
      ul.appendChild(empty);
      return;
    }

    // Render at most 200 rows to keep the DOM snappy. A fuller results count stays visible.
    const slice = items.slice(0, 200);
    slice.forEach((item) => ul.appendChild(renderRow(item)));
    if (items.length > slice.length) {
      const more = document.createElement('li');
      more.className = 'empty';
      more.textContent = `Showing first ${slice.length} of ${items.length}. Refine your search to see more.`;
      ul.appendChild(more);
    }
  }

  function renderRow(item) {
    const li = document.createElement('li');
    const head = document.createElement('div');
    head.className = 'row-head';

    const name = document.createElement('span');
    name.className = 'name';
    name.textContent = item.name;
    head.appendChild(name);

    if (state.view === 'ocs') {
      badge(head, item.type, 'accent');
      badge(head, item.class);
    } else {
      badge(head, item.type);
      badge(head, item.cardinality);
      if (item.deprecated) badge(head, 'deprecated', 'warn');
      if (item.immutable) badge(head, 'immutable');
      if (item.since && item.since.length) badge(head, 'since ' + item.since[item.since.length - 1], 'accent');
    }

    li.appendChild(head);

    const descText = (item.description || '').trim();
    if (descText) {
      const desc = document.createElement('div');
      desc.className = 'row-desc';
      desc.textContent = descText.slice(0, 200);
      li.appendChild(desc);
    }

    li.addEventListener('click', () => {
      const kind = state.view === 'ocs' ? 'oc' : 'attr';
      location.hash = `${kind}=${encodeURIComponent(item.name)}`;
    });
    return li;
  }

  function badge(parent, text, variant) {
    if (!text) return;
    const b = document.createElement('span');
    b.className = 'badge' + (variant ? ' ' + variant : '');
    b.textContent = text;
    parent.appendChild(b);
  }

  function openDetail(kind, item) {
    const host = $('#detail');
    host.classList.remove('hidden');
    host.innerHTML = '';

    const card = document.createElement('div');
    card.className = 'detail-card';

    const close = document.createElement('button');
    close.className = 'close';
    close.setAttribute('aria-label', 'Close');
    close.textContent = '✕';
    card.appendChild(close);

    const h2 = document.createElement('h2');
    h2.textContent = item.name;
    card.appendChild(h2);

    const subtle = document.createElement('div');
    subtle.className = 'subtle';
    subtle.textContent = kind === 'oc' ? 'object class · id ' + item.id : 'attribute · id ' + item.id;
    card.appendChild(subtle);

    const desc = document.createElement('dd');
    desc.className = 'prose';
    desc.textContent = item.description || '—';

    const dl = document.createElement('dl');
    addRow(dl, 'Description', desc);

    if (kind === 'attr') {
      addRow(dl, 'Type', item.type);
      addRow(dl, 'Cardinality', item.cardinality);
      if (item.immutable) addRow(dl, 'Immutable', 'true');
      if (item.deprecated) addRow(dl, 'Deprecated', item.deprecatedSince || 'true');
      if (item.flags.length) addRow(dl, 'Flags', item.flags.join(', '));
      if (item.requiredIn.length) addRow(dl, 'Required in', item.requiredIn.join(', '));
      if (item.optionalIn.length) addRow(dl, 'Optional in', item.optionalIn.join(', '));
      if (item.since.length) addRow(dl, 'Since', item.since.join(', '));
      if (item.min) addRow(dl, 'Min', item.min);
      if (item.max) addRow(dl, 'Max', item.max);
      if (item.enumValues.length) addRow(dl, 'Enum values', item.enumValues.join(', '));
      if (item.globalConfigValues.length) addRow(dl, 'Global config', item.globalConfigValues.join(', '));
      if (item.defaultCosValues.length) addRow(dl, 'Default COS', item.defaultCosValues.join(', '));
      if (item.defaultExternalCosValues.length) addRow(dl, 'Default external COS', item.defaultExternalCosValues.join(', '));
      if (item.requiresRestart.length) addRow(dl, 'Requires restart', item.requiresRestart.join(', '));
    } else {
      if (item.type) addRow(dl, 'Type', item.type);
      if (item.class) addRow(dl, 'Class', item.class);
      if (item.sup && item.sup.length) addRow(dl, 'Superclass', item.sup.join(', '));
      if (item.attrs && item.attrs.length) addRow(dl, 'Attributes', item.attrs.join(', '));
    }
    card.appendChild(dl);

    host.appendChild(card);
  }

  function addRow(dl, label, value) {
    const dt = document.createElement('dt');
    dt.textContent = label;
    dl.appendChild(dt);
    if (value instanceof HTMLElement) {
      dl.appendChild(value);
    } else {
      const dd = document.createElement('dd');
      dd.textContent = value;
      dl.appendChild(dd);
    }
  }

  function closeDetail() {
    $('#detail').classList.add('hidden');
    if (location.hash) history.replaceState(null, '', location.pathname + location.search);
  }
})();
