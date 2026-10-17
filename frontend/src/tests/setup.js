import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

/*
 * jsdom does not implement the browser APIs that recharts and some libraries probe for. Stubbing them
 * once here keeps every test file free of setup noise.
 */

if (!window.matchMedia) {
  window.matchMedia = (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  });
}

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

if (!window.ResizeObserver) {
  window.ResizeObserver = ResizeObserverStub;
}

// recharts measures its container; in jsdom every box is 0x0, so give it a stable size.
if (!HTMLElement.prototype.getBoundingClientRect.__stubbed) {
  const original = HTMLElement.prototype.getBoundingClientRect;
  HTMLElement.prototype.getBoundingClientRect = function getBoundingClientRect() {
    const rect = original.call(this);
    if (rect.width === 0 && rect.height === 0) {
      return { ...rect, width: 600, height: 300, top: 0, left: 0, right: 600, bottom: 300 };
    }
    return rect;
  };
  HTMLElement.prototype.getBoundingClientRect.__stubbed = true;
}

if (!window.HTMLElement.prototype.scrollIntoView) {
  window.HTMLElement.prototype.scrollIntoView = vi.fn();
}

Object.defineProperty(window, 'scrollTo', { value: vi.fn(), writable: true });

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.restoreAllMocks();
});
