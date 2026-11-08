export const EXTENSION_TABS = Object.freeze(['extensions', 'trackers']);

export function isApplyExtensionActionAvailable(actionHandler) {
  return typeof actionHandler === 'function';
}
