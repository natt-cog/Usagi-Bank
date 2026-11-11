<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="顧客一覧" />
<%@ include file="../common/header.jspf" %>
<h1>顧客一覧</h1>
<form method="get" action="${ctx}/customers" class="search">
  <label>氏名 / カナ / CIF番号 <input type="text" name="q" value="<c:out value='${q}'/>" size="30"></label>
  <button type="submit" class="primary">検索</button>
  <sec:authorize access="hasAnyRole('TELLER','ADMIN')"><a href="${ctx}/customers/new" class="button">新規顧客登録</a></sec:authorize>
</form>
<table class="grid">
  <thead><tr><th>CIF番号</th><th>氏名</th><th>フリガナ</th><th>生年月日</th><th>電話番号</th><th>本人確認</th><th>登録日</th></tr></thead>
  <tbody>
  <c:forEach var="cu" items="${customers.content}">
    <tr>
      <td><a href="${ctx}/customers/${cu.cifNo}"><c:out value="${cu.cifNo}" /></a></td>
      <td><c:out value="${cu.nameKanji}" /></td>
      <td class="kana"><c:out value="${cu.nameKana}" /></td>
      <td><fmt:formatDate value="${cu.birthDate}" pattern="yyyy/MM/dd" /></td>
      <td><c:out value="${cu.phone}" /></td>
      <td class="kyc-${cu.kycStatus}"><c:out value="${cu.kycStatus.label}" /></td>
      <td><fmt:formatDate value="${cu.createdAt}" pattern="yyyy/MM/dd" /></td>
    </tr>
  </c:forEach>
  <c:if test="${empty customers.content}"><tr><td colspan="7" class="empty">該当する顧客はいません</td></tr></c:if>
  </tbody>
</table>
<div class="pager">
  <c:if test="${customers.hasPrevious()}"><a href="?q=${fn:escapeXml(q)}&page=${customers.number - 1}">&laquo; 前へ</a></c:if>
  <span><c:out value="${customers.number + 1}" /> / <c:out value="${customers.totalPages == 0 ? 1 : customers.totalPages}" /> ページ</span>
  <c:if test="${customers.hasNext()}"><a href="?q=${fn:escapeXml(q)}&page=${customers.number + 1}">次へ &raquo;</a></c:if>
</div>
<%@ include file="../common/footer.jspf" %>
