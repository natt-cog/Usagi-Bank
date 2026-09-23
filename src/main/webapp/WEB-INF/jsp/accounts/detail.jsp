<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="口座照会" />
<%@ include file="../common/header.jspf" %>
<h1>口座照会 <small><c:out value="${account.displayNo}" /></small></h1>

<div class="row">
  <div class="panel half">
    <table class="kv">
      <tr><th>店番-口座番号</th><td><c:out value="${account.displayNo}" /></td></tr>
      <tr><th>科目</th><td><c:out value="${account.accountType.label}" /></td></tr>
      <tr><th>状態</th><td class="status-${account.status}"><c:out value="${account.status.label}" /></td></tr>
      <tr><th>名義人</th><td><a href="${ctx}/customers/${account.customer.cifNo}"><c:out value="${account.customer.nameKanji}" /></a> (<c:out value="${account.customer.nameKana}" />)</td></tr>
      <tr><th>残高</th><td class="num big"><fmt:formatNumber value="${account.balance}" pattern="#,##0" /> 円</td></tr>
      <tr><th>年利</th><td><c:out value="${account.interestRate}" /> %</td></tr>
      <tr><th>未払利息</th><td><fmt:formatNumber value="${account.accruedInterest}" pattern="#,##0.00" /> 円</td></tr>
      <tr><th>開設日</th><td><fmt:formatDate value="${account.openedOn}" pattern="yyyy/MM/dd" /></td></tr>
    </table>
    <p><a href="${ctx}/accounts/${account.branchCode}/${account.accountNo}/statement.xml" target="_blank">取引明細書 (XML) をダウンロード</a></p>
  </div>
  <sec:authorize access="hasAnyRole('TELLER','ADMIN')">
  <div class="panel half">
    <h2>窓口取引</h2>
    <form method="post" action="${ctx}/accounts/${account.branchCode}/${account.accountNo}/deposit" class="inline-form js-confirm" data-confirm="入金を実行しますか？">
      <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
      <label>入金額 <input type="text" name="amount" class="amount" size="12"></label>
      <label>摘要 <input type="text" name="description" size="20"></label>
      <button type="submit" class="primary">入金</button>
    </form>
    <form method="post" action="${ctx}/accounts/${account.branchCode}/${account.accountNo}/withdraw" class="inline-form js-confirm" data-confirm="出金を実行しますか？">
      <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
      <label>出金額 <input type="text" name="amount" class="amount" size="12"></label>
      <label>摘要 <input type="text" name="description" size="20"></label>
      <button type="submit" class="warn">出金</button>
    </form>
    <sec:authorize access="hasRole('ADMIN')">
    <form method="post" action="${ctx}/accounts/${account.branchCode}/${account.accountNo}/status" class="inline-form js-confirm" data-confirm="口座状態を変更しますか？">
      <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
      <label>口座状態
        <select name="status">
          <c:forEach var="s" items="${statuses}"><option value="${s}" ${account.status == s ? 'selected' : ''}><c:out value="${s.label}" /></option></c:forEach>
        </select></label>
      <button type="submit">変更</button>
    </form>
    </sec:authorize>
  </div>
  </sec:authorize>
</div>

<div class="panel">
  <h2>取引明細 <small>(全 <c:out value="${transactions.totalElements}" /> 件)</small></h2>
  <table class="grid">
    <thead><tr><th>取引日</th><th>種別</th><th>摘要</th><th class="num">お引出し</th><th class="num">お預入れ</th><th class="num">残高</th><th>参照番号</th><th>担当</th></tr></thead>
    <tbody>
    <c:forEach var="t" items="${transactions.content}">
      <tr>
        <td><fmt:formatDate value="${t.valueDate}" pattern="yyyy/MM/dd" /></td>
        <td><c:out value="${t.type.label}" /></td>
        <td><c:out value="${t.description}" /></td>
        <td class="num"><c:if test="${not t.type.credit}"><fmt:formatNumber value="${t.amount}" pattern="#,##0" /></c:if></td>
        <td class="num"><c:if test="${t.type.credit}"><fmt:formatNumber value="${t.amount}" pattern="#,##0" /></c:if></td>
        <td class="num"><fmt:formatNumber value="${t.balanceAfter}" pattern="#,##0" /></td>
        <td><c:out value="${t.referenceNo}" /></td>
        <td><c:out value="${t.operatorId}" /></td>
      </tr>
    </c:forEach>
    <c:if test="${empty transactions.content}"><tr><td colspan="8" class="empty">取引明細はありません</td></tr></c:if>
    </tbody>
  </table>
  <div class="pager">
    <c:if test="${transactions.hasPrevious()}"><a href="?page=${transactions.number - 1}">&laquo; 前へ</a></c:if>
    <span><c:out value="${transactions.number + 1}" /> / <c:out value="${transactions.totalPages == 0 ? 1 : transactions.totalPages}" /> ページ</span>
    <c:if test="${transactions.hasNext()}"><a href="?page=${transactions.number + 1}">次へ &raquo;</a></c:if>
  </div>
</div>
<%@ include file="../common/footer.jspf" %>
