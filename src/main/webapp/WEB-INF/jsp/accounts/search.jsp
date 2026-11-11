<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="口座検索" />
<%@ include file="../common/header.jspf" %>
<h1>口座検索</h1>
<form method="get" action="${ctx}/accounts" class="search">
  <label>店番 <input type="text" name="branchCode" value="<c:out value='${param.branchCode}'/>" size="3" maxlength="3"></label>
  <label>科目
    <select name="type"><option value="">すべて</option>
      <c:forEach var="t" items="${types}"><option value="${t}" ${param.type == t ? 'selected' : ''}><c:out value="${t.label}" /></option></c:forEach>
    </select></label>
  <label>状態
    <select name="status"><option value="">すべて</option>
      <c:forEach var="s" items="${statuses}"><option value="${s}" ${param.status == s ? 'selected' : ''}><c:out value="${s.label}" /></option></c:forEach>
    </select></label>
  <label>残高 <input type="text" name="minBalance" value="<c:out value='${param.minBalance}'/>" size="10"> 〜 <input type="text" name="maxBalance" value="<c:out value='${param.maxBalance}'/>" size="10"></label>
  <button type="submit" class="primary">検索</button>
</form>
<table class="grid">
  <thead><tr><th>店番</th><th>口座番号</th><th>科目</th><th>状態</th><th>名義人</th><th class="num">残高 (円)</th><th>開設日</th></tr></thead>
  <tbody>
  <c:forEach var="a" items="${accounts}">
    <tr class="status-${a.status}">
      <td><c:out value="${a.branchCode}" /></td>
      <td><a href="${ctx}/accounts/${a.branchCode}/${a.accountNo}"><c:out value="${a.accountNo}" /></a></td>
      <td><c:out value="${a.accountType.label}" /></td>
      <td><c:out value="${a.status.label}" /></td>
      <td><c:out value="${a.customer.nameKanji}" /></td>
      <td class="num"><fmt:formatNumber value="${a.balance}" pattern="#,##0" /></td>
      <td><fmt:formatDate value="${a.openedOn}" pattern="yyyy/MM/dd" /></td>
    </tr>
  </c:forEach>
  <c:if test="${empty accounts}"><tr><td colspan="7" class="empty">該当する口座はありません</td></tr></c:if>
  </tbody>
</table>
<%@ include file="../common/footer.jspf" %>
