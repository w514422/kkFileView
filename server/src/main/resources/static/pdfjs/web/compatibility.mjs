const atTargets = [
  Array,
  String,
  Int8Array,
  Uint8Array,
  Uint8ClampedArray,
  Int16Array,
  Uint16Array,
  Int32Array,
  Uint32Array,
  Float32Array,
  Float64Array,
  globalThis.BigInt64Array,
  globalThis.BigUint64Array,
];

function installAtPolyfill(target) {
  if (!target?.prototype || typeof target.prototype.at === "function") {
    return;
  }
  Object.defineProperty(target.prototype, "at", {
    value(index) {
      const length = this.length >>> 0;
      let relativeIndex = Number(index) || 0;
      if (!Number.isFinite(relativeIndex)) {
        relativeIndex = 0;
      }
      relativeIndex = relativeIndex < 0 ? Math.ceil(relativeIndex) : Math.floor(relativeIndex);
      const resolvedIndex = relativeIndex >= 0 ? relativeIndex : length + relativeIndex;
      if (resolvedIndex < 0 || resolvedIndex >= length) {
        return undefined;
      }
      return this[resolvedIndex];
    },
    writable: true,
    configurable: true,
  });
}

for (const target of atTargets) {
  installAtPolyfill(target);
}
