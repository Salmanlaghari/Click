package com.click.browser.engine

/**
 * JavaScript injections backing the Accessibility settings:
 * force zoom, reduced motion, and reader mode.
 *
 * All three are best-effort page transformations injected on page finish —
 * wrapped in try/catch so a hostile page can never break the browser.
 */
object AccessibilityJs {

    /**
     * Force zoom: rewrites the viewport meta tag so pages that declare
     * `user-scalable=no` (or a tiny maximum-scale) become pinch-zoomable.
     * Built-in zoom controls are already enabled at WebView creation.
     */
    const val FORCE_ZOOM_JS = """(function(){try{
var m=document.querySelector('meta[name="viewport"]');
if(m){
  var c=m.getAttribute('content')||'';
  c=c.replace(/user-scalable\s*=\s*no/gi,'user-scalable=yes')
     .replace(/maximum-scale\s*=\s*[\d.]+/gi,'maximum-scale=10');
  if(!/user-scalable/i.test(c)) c+=', user-scalable=yes';
  m.setAttribute('content',c);
}else{
  var n=document.createElement('meta');
  n.setAttribute('name','viewport');
  n.setAttribute('content','width=device-width, initial-scale=1, maximum-scale=10, user-scalable=yes');
  (document.head||document.documentElement).appendChild(n);
}
}catch(e){}})();"""

    /**
     * Reduced motion: collapses CSS animations/transitions and disables
     * smooth scrolling for users sensitive to motion.
     */
    const val REDUCED_MOTION_JS = """(function(){try{
if(document.getElementById('__click_rm'))return;
var s=document.createElement('style');
s.id='__click_rm';
s.textContent='*,*::before,*::after{animation-duration:0.001s!important;animation-iteration-count:1!important;transition-duration:0.001s!important;scroll-behavior:auto!important}';
(document.head||document.documentElement).appendChild(s);
}catch(e){}})();"""

    /**
     * Reader mode: when the page looks like an article (>= 800 chars of text
     * in <article>/<main>/body), hides the original layout behind a clean
     * reading overlay with an "Exit reader" button that restores the page.
     * Returns 'reader' / 'not-article' / 'already' / 'err'.
     */
    const val READER_MODE_JS = """(function(){try{
if(document.getElementById('__click_reader'))return 'already';
var root=document.querySelector('article')||document.querySelector('main')||document.body;
if(!root)return 'not-article';
var text=(root.innerText||'').trim();
if(text.length<800)return 'not-article';
var overlay=document.createElement('div');
overlay.id='__click_reader';
overlay.setAttribute('style','position:fixed;inset:0;z-index:2147483647;overflow-y:auto;background:#ffffff;color:#111111;font-family:Georgia,serif;font-size:19px;line-height:1.7;padding:24px 18px 64px 18px;box-sizing:border-box;');
var inner=document.createElement('div');
inner.setAttribute('style','max-width:700px;margin:0 auto;');
inner.innerHTML=root.innerHTML;
inner.querySelectorAll('img').forEach(function(img){img.style.maxWidth='100%';img.style.height='auto';});
var exit=document.createElement('button');
exit.id='__click_reader_exit';
exit.textContent='Exit reader \u2715';
exit.setAttribute('style','position:sticky;top:0;display:block;margin:0 0 16px auto;padding:10px 18px;border-radius:24px;border:1px solid #ddd;background:#111;color:#fff;font-size:14px;cursor:pointer;');
exit.onclick=function(){overlay.remove();};
overlay.appendChild(exit);
overlay.appendChild(inner);
document.body.appendChild(overlay);
window.scrollTo(0,0);
return 'reader';
}catch(e){return 'err';}})();"""
}
