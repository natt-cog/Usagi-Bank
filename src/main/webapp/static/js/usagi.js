/* うさぎ銀行 端末共通スクリプト (jQuery 1.12 / IE8 対応) */
(function ($) {
    'use strict';

    // 取引前の確認ダイアログ
    $(document).on('submit', 'form.js-confirm', function () {
        var msg = $(this).attr('data-confirm') || '実行しますか？';
        return window.confirm(msg);
    });

    // 金額欄: 全角数字→半角、カンマ除去
    $(document).on('blur', 'input.amount', function () {
        var v = $(this).val();
        v = v.replace(/[０-９]/g, function (c) { return String.fromCharCode(c.charCodeAt(0) - 0xFEE0); });
        v = v.replace(/[,，]/g, '');
        $(this).val(v);
    });

    // 振込画面: 口座番号入力時に名義人を照会 (REST API)
    function lookup(fieldset, target) {
        var branch = fieldset.find('input.branch').val();
        var acct = fieldset.find('input.account').val();
        if (branch.length !== 3 || acct.length !== 7) { target.text(''); return; }
        var ctx = $('#nav a:first').attr('href').replace(/\/dashboard$/, '');
        $.ajax({
            url: ctx + '/api/accounts/' + branch + '/' + acct,
            dataType: 'json',
            success: function (a) { target.text(a.customerName + ' 様 (' + a.accountTypeLabel + ')'); },
            error: function () { target.text('該当口座なし'); }
        });
    }
    $(document).on('change', 'form.transfer input.branch, form.transfer input.account', function () {
        var fs = $(this).closest('fieldset');
        lookup(fs, fs.find('.lookup'));
    });

    // 簡易ソート (テーブルヘッダクリック)
    $(document).on('click', 'table.sortable th', function () {
        var th = $(this), table = th.closest('table'), idx = th.index();
        var rows = table.find('tbody tr').get();
        var asc = !th.hasClass('asc');
        rows.sort(function (a, b) {
            var x = $(a).children('td').eq(idx).text().replace(/,/g, '');
            var y = $(b).children('td').eq(idx).text().replace(/,/g, '');
            var nx = parseFloat(x), ny = parseFloat(y);
            if (!isNaN(nx) && !isNaN(ny)) { return asc ? nx - ny : ny - nx; }
            return asc ? x.localeCompare(y) : y.localeCompare(x);
        });
        $.each(rows, function (i, r) { table.children('tbody').append(r); });
        table.find('th').removeClass('asc desc');
        th.addClass(asc ? 'asc' : 'desc');
    });
})(jQuery);
