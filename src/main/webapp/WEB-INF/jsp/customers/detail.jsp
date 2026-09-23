<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="顧客照会" />
<%@ include file="../common/header.jspf" %>
<h1>顧客照会 <small>CIF <c:out value="${customer.cifNo}" /></small></h1>
<div class="row">
  <div class="panel half">
    <table class="kv">
      <tr><th>氏名</th><td><c:out value="${customer.nameKanji}" /></td></tr>
      <tr><th>フリガナ</th><td class="kana"><c:out value="${customer.nameKana}" /></td></tr>
      <tr><th>生年月日</th><td><fmt:formatDate value="${customer.birthDate}" pattern="yyyy年MM月dd日" /></td></tr>
      <tr><th>住所</th><td>〒<c:out value="${customer.postalCode}" /> <c:out value="${customer.address}" /></td></tr>
      <tr><th>電話番号</th><td><c:out value="${customer.phone}" /></td></tr>
      <tr><th>本人確認</th><td class="kyc-${customer.kycStatus}"><c:out value="${customer.kycStatus.label}" /></td></tr>
      <tr><th>登録日</th><td><fmt:formatDate value="${customer.createdAt}" pattern="yyyy/MM/dd" /></td></tr>
    </table>
    <sec:authorize access="hasAnyRole('TELLER','ADMIN')">
    <form method="post" action="${ctx}/customers/${customer.cifNo}/kyc" class="inline-form">
      <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
      <label>本人確認ステータス
        <select name="status">
          <c:forEach var="k" items="${kycStatuses}"><option value="${k}" ${customer.kycStatus == k ? 'selected' : ''}><c:out value="${k.label}" /></option></c:forEach>
        </select></label>
      <button type="submit">更新</button>
    </form>
    </sec:authorize>
  </div>
  <sec:authorize access="hasAnyRole('TELLER','ADMIN')">
  <div class="panel half">
    <h2>口座開設</h2>
    <form method="post" action="${ctx}/customers/${customer.cifNo}/accounts" class="inline-form js-confirm" data-confirm="口座を開設しますか？">
      <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
      <label>取扱店
        <select name="branchCode">
          <c:forEach var="b" items="${branches}"><option value="${b.code}"><c:out value="${b.code}" /> <c:out value="${b.name}" /></option></c:forEach>
        </select></label>
      <label>科目
        <select name="type">
          <c:forEach var="t" items="${types}"><option value="${t}"><c:out value="${t.label}" /></option></c:forEach>
        </select></label>
      <label>初回入金額 <input type="text" name="initialDeposit" class="amount" size="12" value="0"></label>
      <button type="submit" class="primary">開設</button>
    </form>
    <c:if test="${customer.kycStatus != 'VERIFIED'}"><p class="note">※ 本人確認が「確認済」でない顧客の口座開設はできません。</p></c:if>
  </div>
  </sec:authorize>
</div>
<div class="panel">
  <h2>保有口座</h2>
  <table class="grid">
    <thead><tr><th>店番</th><th>口座番号</th><th>科目</th><th>状態</th><th class="num">残高 (円)</th><th>年利 (%)</th><th>開設日</th></tr></thead>
    <tbody>
    <c:forEach var="a" items="${accounts}">
      <tr class="status-${a.status}">
        <td><c:out value="${a.branchCode}" /></td>
        <td><a href="${ctx}/accounts/${a.branchCode}/${a.accountNo}"><c:out value="${a.accountNo}" /></a></td>
        <td><c:out value="${a.accountType.label}" /></td>
        <td><c:out value="${a.status.label}" /></td>
        <td class="num"><fmt:formatNumber value="${a.balance}" pattern="#,##0" /></td>
        <td class="num"><c:out value="${a.interestRate}" /></td>
        <td><fmt:formatDate value="${a.openedOn}" pattern="yyyy/MM/dd" /></td>
      </tr>
    </c:forEach>
    <c:if test="${empty accounts}"><tr><td colspan="7" class="empty">口座はありません</td></tr></c:if>
    </tbody>
  </table>
</div>
<%@ include file="../common/footer.jspf" %>
