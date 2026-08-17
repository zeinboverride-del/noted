const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function createElementStub() {
  return {
    style: {},
    textContent: '',
    innerHTML: '',
    value: '',
    disabled: false,
    dataset: {},
    classList: {
      add() {},
      remove() {},
      toggle() {},
      contains() { return false; }
    },
    addEventListener() {},
    querySelector() { return createElementStub(); },
    querySelectorAll() { return []; },
    focus() {},
    insertAdjacentHTML() {}
  };
}

function buildContext() {
  const elements = new Map();
  const getStub = (id) => {
    if (!elements.has(id)) {
      elements.set(id, createElementStub());
    }
    return elements.get(id);
  };

  const storage = {};
  const document = {
    querySelectorAll() { return []; },
    querySelector(selector) {
      if (selector === '.isi_hari') return createElementStub();
      if (selector === '.hari') return createElementStub();
      if (selector === '.questinday') return createElementStub();
      if (selector === 'input[type="datetime-local"], input[name="targetDateTime"], [data-task-datetime]') return null;
      return createElementStub();
    },
    getElementById(id) { return getStub(id); },
    createElement() { return createElementStub(); }
  };

  const context = {
    console,
    document,
    localStorage: {
      getItem(key) { return Object.prototype.hasOwnProperty.call(storage, key) ? storage[key] : null; },
      setItem(key, val) { storage[key] = String(val); },
      removeItem(key) { delete storage[key]; }
    },
    navigator: {
      serviceWorker: { register: async () => ({ scope: './' }) }
    },
    window: {
      addEventListener() {},
      removeEventListener() {},
      history: { pushState() {} },
      navigator: { serviceWorker: { register: async () => ({ scope: './' }) } },
      Capacitor: undefined
    },
    history: { pushState() {} },
    Intl,
    Date,
    setTimeout,
    clearTimeout
  };

  return context;
}

const context = buildContext();
vm.createContext(context);
const code = fs.readFileSync('./www/indexan.js', 'utf8');
vm.runInContext(code, context);

assert.equal(typeof context.buildDailyReminderNotifications, 'function');

const result = context.buildDailyReminderNotifications(new Date('2026-08-17T00:00:00'), {
  Senin: [{ judul: 'Tugas A', selesai: false }],
  Selasa: [{ judul: 'Tugas B', selesai: false }],
  Khusus: [{ judul: 'Event', selesai: false }]
});

assert.equal(result.length, 3);
assert.equal(result[0].title, 'Tugas Hari Ini');
assert.match(result[0].body, /Tugas A/);
assert.equal(result[1].title, 'Tugas Hari Esok');
assert.equal(result[2].title, 'Ringkasan Hari Ini & Besok');

console.log('Daily notification logic test passed.');
