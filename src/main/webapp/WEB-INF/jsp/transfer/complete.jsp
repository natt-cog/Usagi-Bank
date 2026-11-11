<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="振込完了" />
<%@ include file="../common/header.jspf" %>
<h1>振込完了</h1>
<div class="panel">
  <div class="flash ok">振込を受け付けました。参照番号: <strong><c:out value="${result.referenceNo}" /></strong></div>
  <table class="kv">
    <tr><th>出金口座</th><td><c:out value="${result.debit.account.displayNo}" /> <c:out value="${result.debit.account.customer.nameKanji}" /></td></tr>
    <tr><th>入金口座</th><td><c:out value="${result.credit.account.displayNo}" /> <c:out value="${result.credit.account.customer.nameKanji}" /></td></tr>
    <tr><th>振込金額</th><td class="num"><fmt:formatNumber value="${result.credit.amount}" pattern="#,##0" /> 円</td></tr>
    <tr><th>振込手数料</th><td class="num"><fmt:formatNumber value="${result.feeAmount}" pattern="#,##0" /> 円</td></tr>
    <tr><th>出金後残高</th><td class="num"><fmt:formatNumber value="${result.fee != null ? result.fee.balanceAfter : result.debit.balanceAfter}" pattern="#,##0" /> 円</td></tr>
    <tr><th>取引日時</th><td><fmt:formatDate value="${result.debit.postedAt}" pattern="yyyy/MM/dd HH:mm:ss" /></td></tr>
  </table>
  <p><a href="${ctx}/transfer">続けて振込する</a> | <a href="${ctx}/accounts/${result.debit.account.branchCode}/${result.debit.account.accountNo}">出金口座の明細を見る</a></p>
</div>
<%@ include file="../common/footer.jspf" %>
