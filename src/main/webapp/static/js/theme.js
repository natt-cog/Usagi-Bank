/* うさぎ銀行 画面テーマ (ライト/ダーク) 切替. <head> 内で同期読込し、描画前にテーマを適用する. */
(function (window, document) {
    'use strict';

    var KEY = 'usagi.theme';
    var root = document.documentElement;

    function load() {
        try { return window.localStorage.getItem(KEY); } catch (e) { return null; }
    }

    function save(theme) {
        try { window.localStorage.setItem(KEY, theme); } catch (e) { /* 保存不可の端末では画面内のみ反映 */ }
    }

    function prefersDark() {
        return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
    }

    function isDark() {
        return (' ' + root.className + ' ').indexOf(' dark ') >= 0;
    }

    function apply(theme) {
        var cls = (' ' + root.className + ' ').replace(' dark ', ' ');
        root.className = (theme === 'dark' ? cls + 'dark' : cls).replace(/^\s+|\s+$/g, '');
        var btn = document.getElementById('theme-toggle');
        if (btn) {
            btn.innerHTML = theme === 'dark' ? '&#9728; ライトモード' : '&#9790; ダークモード';
            btn.setAttribute('aria-pressed', theme === 'dark' ? 'true' : 'false');
        }
    }

    function current() {
        return isDark() ? 'dark' : 'light';
    }

    apply(load() || (prefersDark() ? 'dark' : 'light'));

    function onClick(e) {
        e = e || window.event;
        var t = e.target || e.srcElement;
        if (!t || t.id !== 'theme-toggle') { return; }
        var next = current() === 'dark' ? 'light' : 'dark';
        save(next);
        apply(next);
    }

    function onReady() {
        apply(current());
    }

    if (document.addEventListener) {
        document.addEventListener('click', onClick, false);
        document.addEventListener('DOMContentLoaded', onReady, false);
    } else {
        document.attachEvent('onclick', onClick);
        window.attachEvent('onload', onReady);
    }
})(window, document);
