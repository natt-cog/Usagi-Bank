<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="新規顧客登録" />
<%@ include file="../common/header.jspf" %>
<h1>新規顧客登録</h1>
<form:form modelAttribute="customer" method="post" action="${ctx}/customers" cssClass="panel">
  <table class="kv form">
    <tr><th>氏名 (漢字) <span class="req">必須</span></th><td><form:input path="nameKanji" size="30" /> <form:errors path="nameKanji" cssClass="err" /></td></tr>
    <tr><th>フリガナ (全角カナ) <span class="req">必須</span></th><td><form:input path="nameKana" size="30" /> <form:errors path="nameKana" cssClass="err" /></td></tr>
    <tr><th>生年月日</th><td><form:input path="birthDate" placeholder="yyyy/MM/dd" size="10" /> <form:errors path="birthDate" cssClass="err" /></td></tr>
    <tr><th>郵便番号</th><td><form:input path="postalCode" placeholder="100-0001" size="8" maxlength="8" /> <form:errors path="postalCode" cssClass="err" /></td></tr>
    <tr><th>住所</th><td><form:input path="address" size="60" /> <form:errors path="address" cssClass="err" /></td></tr>
    <tr><th>電話番号</th><td><form:input path="phone" placeholder="03-1234-5678" size="14" /> <form:errors path="phone" cssClass="err" /></td></tr>
  </table>
  <button type="submit" class="primary">登録</button> <a href="${ctx}/customers">キャンセル</a>
</form:form>
<%@ include file="../common/footer.jspf" %>
