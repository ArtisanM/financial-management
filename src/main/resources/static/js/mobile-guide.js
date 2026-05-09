/* 家庭账房 v0.2 · 移动端引导(FR-33 微信 + FR-34 iOS PWA)
 * 渐进增强 — 失败/不支持环境完全静默。
 * 详见 tech-design/v0.2.md § 3
 */
(function () {
  'use strict';

  // ---------- Storage 降级(隐私模式 / 沙箱可能抛) ----------
  var S = (function () {
    try {
      localStorage.setItem('__t__', '1');
      localStorage.removeItem('__t__');
      return localStorage;
    } catch (e) {
      return {
        getItem: function () { return null; },
        setItem: function () {},
        removeItem: function () {}
      };
    }
  })();

  // ---------- UA 检测 ----------
  var ua = navigator.userAgent;
  var isWX = /MicroMessenger/i.test(ua);
  var isIOS = /iPad|iPhone|iPod/.test(ua);
  var isSafari = /^((?!chrome|android|crios|fxios).)*safari/i.test(ua);
  var inStandalone = window.navigator.standalone === true;

  // ---------- DOM helpers(全部用安全 API) ----------
  function injectStyle(id, css) {
    if (document.getElementById(id)) return;
    var s = document.createElement('style');
    s.id = id;
    s.textContent = css;
    document.head.appendChild(s);
  }
  function el(tag, css, text) {
    var e = document.createElement(tag);
    if (css) e.style.cssText = css;
    if (text != null) e.textContent = text;
    return e;
  }
  function svgEl(tag, attrs) {
    var e = document.createElementNS('http://www.w3.org/2000/svg', tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (k) { e.setAttribute(k, attrs[k]); });
    }
    return e;
  }

  // ============ FR-33 微信引导 ============
  function showWxGuide() {
    if (Date.now() - (+S.getItem('wx_dismissed_at') || 0) < 86400000) return;

    injectStyle('wx-guide-keyframes',
      '@keyframes wxBounce{0%,100%{transform:translateY(0)}50%{transform:translateY(-8px)}}');

    var mask = el('div',
      'position:fixed;inset:0;background:rgba(20,16,12,.78);' +
      'backdrop-filter:blur(2px);-webkit-backdrop-filter:blur(2px);z-index:99999;' +
      'font-family:-apple-system,BlinkMacSystemFont,"PingFang SC",sans-serif;');
    mask.id = 'wx-guide-mask';
    mask.setAttribute('role', 'dialog');
    mask.setAttribute('aria-modal', 'true');

    // 大箭头 SVG(指向右上 ⋯)
    var svg = svgEl('svg', { viewBox: '0 0 60 76' });
    svg.style.cssText = 'position:absolute;top:8px;right:24px;width:60px;height:76px;' +
      'animation:wxBounce 1.4s ease-in-out infinite;';
    svg.appendChild(svgEl('path', {
      d: 'M30 70 Q 34 35, 50 8',
      stroke: '#d6a04a', 'stroke-width': '3',
      'stroke-linecap': 'round', fill: 'none', 'stroke-dasharray': '5 4'
    }));
    svg.appendChild(svgEl('path', {
      d: 'M50 8 L 42 14 M50 8 L 56 16',
      stroke: '#d6a04a', 'stroke-width': '3', 'stroke-linecap': 'round'
    }));
    mask.appendChild(svg);

    // 引导卡
    var card = el('div',
      'position:absolute;top:30%;left:24px;right:24px;' +
      'background:#fdf8ef;border:1px solid #1a1714;' +
      'box-shadow:0 14px 40px -8px rgba(0,0,0,.5);padding:22px;');

    card.appendChild(el('h3',
      'font:500 18px/1.2 "Noto Serif SC",serif;margin:0 0 10px;color:#1a1714;',
      '📦 在浏览器中打开'));

    card.appendChild(el('p',
      'font-size:13px;color:#3a3530;line-height:1.55;margin:0 0 14px;',
      '微信内浏览器对账房功能支持不全,建议改在 Safari 或 Chrome 中打开,可获得完整体验。'));

    var ol = el('ol',
      'margin:0 0 14px;padding:0;list-style:none;display:flex;flex-direction:column;gap:8px;');
    [['1', '点击右上角 ⋯ 按钮'], ['2', '选择「在浏览器中打开」']].forEach(function (step) {
      var li = el('li',
        'display:flex;align-items:baseline;gap:10px;font-size:13px;color:#3a3530;');
      var num = el('span',
        'width:22px;height:22px;background:#1a1714;color:#fdf8ef;border-radius:50%;' +
        'display:inline-flex;align-items:center;justify-content:center;' +
        'font:500 11px monospace;flex-shrink:0;',
        step[0]);
      li.appendChild(num);
      li.appendChild(document.createTextNode(step[1]));
      ol.appendChild(li);
    });
    card.appendChild(ol);

    var btn = el('button',
      'width:100%;padding:9px;border:1px solid #1a1714;background:transparent;' +
      'font:11px/1 monospace;letter-spacing:.1em;color:#1a1714;cursor:pointer;',
      '我知道了 · 继续在微信用');
    btn.addEventListener('click', function () {
      S.setItem('wx_dismissed_at', String(Date.now()));
      mask.remove();
    });
    card.appendChild(btn);

    card.appendChild(el('div',
      'text-align:center;font:9px monospace;color:#888;margin-top:8px;letter-spacing:.18em;',
      '24 小时内不再提示'));

    mask.appendChild(card);
    document.body.appendChild(mask);
  }

  // ============ FR-34 iOS PWA Banner ============
  function showIosPwaBanner() {
    if (Date.now() - (+S.getItem('pwa_dismissed_at') || 0) < 7 * 86400000) return;

    injectStyle('pwa-banner-keyframes',
      '@keyframes pwaSlideUp{from{transform:translateY(110%);opacity:0}to{transform:translateY(0);opacity:1}}');

    var banner = el('div',
      'position:fixed;left:10px;right:10px;bottom:90px;z-index:99998;' +
      'background:#fdf8ef;border:1px solid #1a1714;' +
      'box-shadow:0 -8px 28px -4px rgba(0,0,0,.18);padding:14px;' +
      'animation:pwaSlideUp .4s ease-out;' +
      'font-family:"Source Serif 4","Noto Serif SC",serif;');
    banner.id = 'pwa-banner';
    banner.setAttribute('role', 'region');
    banner.setAttribute('aria-label', '添加到主屏');

    // 关闭 ✕
    var x = el('button',
      'position:absolute;top:6px;right:8px;width:22px;height:22px;border:0;' +
      'background:transparent;color:#888;font-size:14px;cursor:pointer;',
      '✕');
    x.setAttribute('aria-label', '关闭');
    x.addEventListener('click', function () { banner.remove(); });
    banner.appendChild(x);

    // 头(图标 + 标题)
    var head = el('div', 'display:flex;gap:12px;align-items:flex-start;margin-bottom:10px;');
    head.appendChild(el('div',
      'width:44px;height:44px;border-radius:11px;flex-shrink:0;' +
      'background:linear-gradient(135deg,#c0915a,#7a5a39);' +
      'display:flex;align-items:center;justify-content:center;color:#fdf8ef;' +
      'font:500 22px "Fraunces",serif;box-shadow:0 2px 8px -1px rgba(122,90,57,.45);',
      '账'));
    var txtwrap = el('div', 'flex:1;padding-top:2px;');
    txtwrap.appendChild(el('h3',
      'font:500 15px/1.2 "Fraunces","Noto Serif SC",serif;color:#1a1714;margin:0;',
      '添加到主屏 · 像 App 一样使用'));
    txtwrap.appendChild(el('p',
      'font:9px/1 monospace;letter-spacing:.18em;color:#888;margin:3px 0 0;text-transform:uppercase;',
      'FAMILY · COUNTING · HOUSE'));
    head.appendChild(txtwrap);
    banner.appendChild(head);

    // 步骤(iOS 18 实际:⋯ → 共享 → 更多 → 添加到主屏幕)
    var steps = el('ol', 'margin:0;padding:0;list-style:none;');
    [
      ['1', '底部地址栏右下「⋯」→ 选「共享」'],
      ['2', '共享面板底部点「⋯ 更多」展开'],
      ['3', '滚动找到「添加到主屏幕」→ 添加']
    ].forEach(function (step) {
      var li = el('li',
        'display:flex;align-items:baseline;gap:6px;font:12px/1.4 inherit;color:#3a3530;padding:2px 0;');
      li.appendChild(el('span',
        'width:18px;height:18px;background:#1a1714;color:#fdf8ef;border-radius:50%;' +
        'display:inline-flex;align-items:center;justify-content:center;' +
        'font:10px monospace;flex-shrink:0;',
        step[0]));
      li.appendChild(document.createTextNode(step[1]));
      steps.appendChild(li);
    });
    banner.appendChild(steps);

    // 「看图详细」按钮(突出,主色)
    var seeBtn = el('button',
      'width:100%;padding:9px;margin-top:10px;border:0;background:#1a1714;' +
      'color:#fdf8ef;font:500 12px/1 "Fraunces","Noto Serif SC",serif;' +
      'cursor:pointer;letter-spacing:.05em;',
      '📷  看真机操作截图(4 张)');
    seeBtn.addEventListener('click', function () { showIosPwaScreenshots(); });
    banner.appendChild(seeBtn);

    // 操作行
    var acts = el('div',
      'display:flex;gap:6px;margin-top:6px;');
    var ok = el('button',
      'flex:1;padding:7px;border:1px solid #c8c2b6;background:transparent;' +
      'font:10px monospace;letter-spacing:.1em;cursor:pointer;color:#1a1714;',
      '知道了');
    ok.addEventListener('click', function () { banner.remove(); });
    var skip = el('button',
      'flex:1;padding:7px;border:0;background:transparent;' +
      'font:10px monospace;letter-spacing:.1em;cursor:pointer;color:#888;',
      '7 天内不再提示');
    skip.addEventListener('click', function () {
      S.setItem('pwa_dismissed_at', String(Date.now()));
      banner.remove();
    });
    acts.appendChild(ok);
    acts.appendChild(skip);
    banner.appendChild(acts);

    document.body.appendChild(banner);
  }

  // ============ FR-34 截图 modal(4 张真机操作截图)============
  function showIosPwaScreenshots() {
    if (document.getElementById('pwa-shots-modal')) return;

    injectStyle('pwa-shots-keyframes',
      '@keyframes pwaShotsFadeIn{from{opacity:0}to{opacity:1}}' +
      '@keyframes pwaShotsSlideUp{from{transform:translateY(20px);opacity:0}to{transform:translateY(0);opacity:1}}');

    var modal = el('div',
      'position:fixed;inset:0;z-index:100000;background:rgba(0,0,0,.88);' +
      'overflow-y:auto;-webkit-overflow-scrolling:touch;' +
      'animation:pwaShotsFadeIn .25s ease-out;' +
      'font-family:"Source Serif 4","Noto Serif SC",serif;');
    modal.id = 'pwa-shots-modal';
    modal.setAttribute('role', 'dialog');
    modal.setAttribute('aria-modal', 'true');
    modal.setAttribute('aria-label', '添加到主屏 · 4 步真机截图');

    // 关闭 ✕(右上 sticky)
    var closeBtn = el('button',
      'position:fixed;top:14px;right:14px;width:38px;height:38px;border:0;' +
      'background:rgba(255,255,255,.12);color:#fff;' +
      'border-radius:50%;font-size:18px;cursor:pointer;z-index:1;backdrop-filter:blur(8px);',
      '✕');
    closeBtn.setAttribute('aria-label', '关闭');
    closeBtn.addEventListener('click', function () { modal.remove(); });
    modal.appendChild(closeBtn);

    // 内容容器(限宽,居中)
    var box = el('div',
      'max-width:520px;margin:0 auto;padding:60px 20px 40px;' +
      'animation:pwaShotsSlideUp .4s ease-out;');

    // 标题
    var ti = el('h2',
      'font:500 26px/1.2 "Fraunces","Noto Serif SC",serif;color:#fdf8ef;margin:0 0 6px;text-align:center;',
      '添加到主屏 · 4 步');
    box.appendChild(ti);
    box.appendChild(el('p',
      'font:10px/1 monospace;letter-spacing:.22em;color:#a09487;text-align:center;margin:0 0 28px;text-transform:uppercase;',
      'iOS · SAFARI · ADD TO HOME SCREEN'));

    // 4 步
    var steps = [
      ['01', '底部地址栏 ⋯ 按钮',
       '在 Safari 底部地址栏右下角,点击「⋯」(更多)按钮 — 红圈位置。',
       '/img/safari-screen/step1.jpg?v=' + (window.__buildVersion || Date.now())],
      ['02', '选择「共享」',
       '弹出菜单的第一项即「共享」。注意中文版 iOS 叫「共享」不叫「分享」。',
       '/img/safari-screen/step2.jpg?v=' + (window.__buildVersion || Date.now())],
      ['03', '共享面板底部「⋯ 更多」',
       '共享面板默认不显示「添加到主屏幕」。在底部应用图标行右侧点「⋯ 更多」展开完整列表。',
       '/img/safari-screen/step3.jpg?v=' + (window.__buildVersion || Date.now())],
      ['04', '选「添加到主屏幕」',
       '展开后向下滚动找到「添加到主屏幕」(红圈),点击 → 编辑页 → 右上角「添加」即完成。',
       '/img/safari-screen/step4.jpg?v=' + (window.__buildVersion || Date.now())]
    ];

    steps.forEach(function (s, i) {
      var fig = el('figure', 'margin:0 0 36px;');
      var head = el('div', 'display:flex;align-items:baseline;gap:14px;margin-bottom:12px;');
      head.appendChild(el('span',
        'font:500 36px/1 "Fraunces",serif;color:#d6a04a;flex-shrink:0;',
        s[0]));
      var headRight = el('div', 'flex:1;');
      headRight.appendChild(el('h3',
        'font:500 17px/1.2 "Fraunces","Noto Serif SC",serif;color:#fdf8ef;margin:0 0 2px;',
        s[1]));
      headRight.appendChild(el('p',
        'font:11px/1.5 inherit;color:#a09487;margin:0;',
        s[2]));
      head.appendChild(headRight);
      fig.appendChild(head);

      // 截图(带边框模拟 iPhone)— eager 加载,iOS Safari 在 fixed modal 里 lazy 不触发
      var frame = el('div',
        'background:#1d1d1f;border-radius:32px;padding:6px;' +
        'box-shadow:0 14px 40px -8px rgba(0,0,0,.6);max-width:280px;margin:0 auto;');
      var img = document.createElement('img');
      img.alt = 'STEP ' + s[0] + ' · ' + s[1];
      img.decoding = 'async';
      img.style.cssText = 'width:100%;display:block;border-radius:26px;background:#2c2c2e;min-height:120px;';
      img.addEventListener('error', function () {
        img.style.cssText += 'padding:40px 20px;text-align:center;color:#ff6b6b;font:12px monospace;';
        img.alt = '⚠ 截图加载失败:' + s[3];
      });
      // 显式设置 src 在所有事件 listener 之后,确保事件能捕获
      img.src = s[3];
      frame.appendChild(img);
      fig.appendChild(frame);

      box.appendChild(fig);
    });

    // 底部"我知道了"
    var done = el('button',
      'display:block;width:200px;margin:20px auto 0;padding:11px;' +
      'background:#fdf8ef;border:0;color:#1a1714;' +
      'font:500 13px monospace;letter-spacing:.18em;cursor:pointer;text-transform:uppercase;',
      '✓  我知道了');
    done.addEventListener('click', function () { modal.remove(); });
    box.appendChild(done);

    modal.appendChild(box);

    // 点遮罩(modal 本体非内容)关闭
    modal.addEventListener('click', function (e) {
      if (e.target === modal) modal.remove();
    });

    document.body.appendChild(modal);
  }

  // ============ Dev escape: ?reset_pwa=1 / ?reset_wx=1 清 dismiss 强制重弹 ============
  (function () {
    var qs = window.location.search || '';
    if (qs.indexOf('reset_pwa=1') > -1) S.removeItem('pwa_dismissed_at');
    if (qs.indexOf('reset_wx=1') > -1) S.removeItem('wx_dismissed_at');
  })();

  // ============ 触发分发(微信优先互斥) ============
  function dispatch() {
    if (isWX) {
      showWxGuide();
    } else if (isIOS && isSafari && !inStandalone) {
      setTimeout(showIosPwaBanner, 1500);
    }
    // 其它环境:静默
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', dispatch);
  } else {
    dispatch();
  }
})();
