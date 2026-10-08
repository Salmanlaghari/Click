// ==UserScript==
// @name         YouTube Volume Booster
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Adds a gain slider to YouTube videos so quiet uploads can be amplified up to 3x. Playback only — no downloading, no ad skipping.
// @match        *://*.youtube.com/*
// @match        *://youtube.com/*
// @grant        GM_addStyle
// @grant        GM_setValue
// @grant        GM_getValue
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;
    if (document.getElementById('click-yt-vol')) return;

    var ctx = null, gainNode = null, sourceNode = null, currentVideo = null;

    function ensureGraph(video) {
      try {
        if (currentVideo === video && gainNode) return true;
        if (!window.AudioContext && !window.webkitAudioContext) return false;
        if (!ctx) ctx = new (window.AudioContext || window.webkitAudioContext)();
        if (ctx.state === 'suspended') { try { ctx.resume(); } catch (e) {} }
        // Tear down the previous source (a video element can only be used once as a source).
        try { if (sourceNode) sourceNode.disconnect(); } catch (e) {}
        try { if (gainNode) gainNode.disconnect(); } catch (e) {}
        sourceNode = ctx.createMediaElementSource(video);
        gainNode = ctx.createGain();
        var saved = 1;
        try { saved = parseFloat(GM_getValue('yt_boost', '1')) || 1; } catch (e) {}
        saved = Math.max(1, Math.min(3, saved));
        gainNode.gain.value = saved;
        sourceNode.connect(gainNode);
        gainNode.connect(ctx.destination);
        currentVideo = video;
        return true;
      } catch (e) { return false; }
    }

    function findVideo() {
      try { return document.querySelector('video.html5-main-video') || document.querySelector('video'); }
      catch (e) { return null; }
    }

    var box = document.createElement('div');
    box.id = 'click-yt-vol';
    box.innerHTML =
      '<span id="click-yt-vol-icon">🔊</span>' +
      '<input id="click-yt-vol-slider" type="range" min="100" max="300" value="100" step="5">' +
      '<span id="click-yt-vol-label">100%</span>';
    document.documentElement.appendChild(box);

    GM_addStyle([
      '#click-yt-vol{position:fixed;left:12px;bottom:76px;z-index:2147483647;display:flex;align-items:center;gap:6px;',
      ' background:rgba(20,20,20,.85);border:1px solid rgba(255,255,255,.2);border-radius:20px;padding:6px 10px;}',
      '#click-yt-vol-icon{font-size:14px;}',
      '#click-yt-vol-slider{width:90px;accent-color:#ff0033;}',
      '#click-yt-vol-label{color:#fff;font-size:11px;min-width:38px;font-family:sans-serif;}'
    ].join(''));

    var slider = document.getElementById('click-yt-vol-slider');
    var label = document.getElementById('click-yt-vol-label');
    try {
      var saved = parseFloat(GM_getValue('yt_boost', '100')) || 100;
      // Stored as percent for the slider; gain uses /100.
      var pct = Math.max(100, Math.min(300, Math.round(saved)));
      slider.value = String(pct);
      label.textContent = pct + '%';
    } catch (e) {}

    slider.addEventListener('input', function () {
      try {
        var pct = parseInt(slider.value, 10) || 100;
        label.textContent = pct + '%';
        try { GM_setValue('yt_boost', String(pct)); } catch (e) {}
        var v = findVideo();
        if (v && ensureGraph(v) && gainNode) {
          gainNode.gain.value = pct / 100;
        }
      } catch (e) {}
    });

    // Attach the graph whenever a video starts playing (YouTube swaps video elements).
    document.addEventListener('play', function (e) {
      try {
        var t = e.target;
        if (t && t.tagName === 'VIDEO') {
          var pct = parseInt(slider.value, 10) || 100;
          if (ensureGraph(t) && gainNode) gainNode.gain.value = pct / 100;
        }
      } catch (err) {}
    }, true);
  } catch (e) { /* never break the host page */ }
})();
