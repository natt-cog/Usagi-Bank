<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="振込" />
<%@ include file="../common/header.jspf" %>
<h1>振込 <small>(行内振込)</small></h1>
<form:form modelAttribute="transfer" method="post" action="${ctx}/transfer" cssClass="panel transfer js-confirm" data-confirm="振込を実行しますか？">
  <form:errors path="*" cssClass="flash ng" element="div" />
  <fieldset>
    <legend>出金口座</legend>
    <label>店番 <form:input path="fromBranchCode" size="3" maxlength="3" cssClass="branch" /></label>
    <label>口座番号 <form:input path="fromAccountNo" size="7" maxlength="7" cssClass="account" /></label>
    <span class="lookup" id="fromLookup"></span>
  </fieldset>
  <fieldset>
    <legend>入金口座</legend>
    <label>店番 <form:input path="toBranchCode" size="3" maxlength="3" cssClass="branch" /></label>
    <label>口座番号 <form:input path="toAccountNo" size="7" maxlength="7" cssClass="account" /></label>
    <span class="lookup" id="toLookup"></span>
  </fieldset>
  <fieldset>
    <legend>振込内容</legend>
    <label>金額 (円) <form:input path="amount" size="12" cssClass="amount" /></label>
    <label>摘要 <form:input path="description" size="30" maxlength="60" /></label>
  </fieldset>
  <p class="note">手数料: 同一店内 0円 / 他店宛 3万円未満 110円・3万円以上 220円。1日の振込限度額は <fmt:formatNumber value="${dailyLimit}" pattern="#,##0" /> 円です。</p>
  <button type="submit" class="primary">振込実行</button>
</form:form>
<%@ include file="../common/footer.jspf" %>
