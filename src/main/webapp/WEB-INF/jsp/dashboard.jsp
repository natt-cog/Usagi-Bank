<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="ダッシュボード" />
<%@ include file="common/header.jspf" %>
<h1>ダッシュボード <small>営業日: <fmt:formatDate value="${businessDate}" pattern="yyyy年MM月dd日" /></small></h1>

<div class="row">
  <div class="panel half">
    <h2>店別預金残高</h2>
    <table class="grid">
      <thead><tr><th>店番</th><th>店名</th><th class="num">残高合計 (円)</th></tr></thead>
      <tbody>
      <c:forEach var="e" items="${branchTotals}">
        <tr>
          <td><c:out value="${e.key.code}" /></td>
          <td><c:out value="${e.key.name}" /></td>
          <td class="num"><fmt:formatNumber value="${e.value}" pattern="#,##0" /></td>
        </tr>
      </c:forEach>
      </tbody>
    </table>
  </div>
  <div class="panel half">
    <h2>預金残高上位顧客</h2>
    <ol class="top">
    <c:forEach var="cu" items="${topCustomers}">
      <li><a href="${ctx}/customers/${cu.cifNo}"><c:out value="${cu.nameKanji}" /></a> <span class="kana"><c:out value="${cu.nameKana}" /></span></li>
    </c:forEach>
    </ol>
  </div>
</div>

<div class="panel">
  <h2>有効口座一覧 <small>(<c:out value="${accountCount}" />件)</small></h2>
  <table class="grid sortable" id="accountTable">
    <thead>
      <tr><th>店番</th><th>口座番号</th><th>科目</th><th>名義人</th><th class="num">残高 (円)</th><th>年利 (%)</th><th>最終取引日</th></tr>
    </thead>
    <tbody>
    <c:forEach var="a" items="${accounts}">
      <tr>
        <td><c:out value="${a.branchCode}" /></td>
        <td><a href="${ctx}/accounts/${a.branchCode}/${a.accountNo}"><c:out value="${a.accountNo}" /></a></td>
        <td><c:out value="${a.accountType.label}" /></td>
        <td><c:out value="${a.customer.nameKanji}" /></td>
        <td class="num"><fmt:formatNumber value="${a.balance}" pattern="#,##0" /></td>
        <td class="num"><c:out value="${a.interestRate}" /></td>
        <td><fmt:formatDate value="${a.lastTransactionOn}" pattern="yyyy/MM/dd" /></td>
      </tr>
    </c:forEach>
    </tbody>
  </table>
</div>
<%@ include file="common/footer.jspf" %>
