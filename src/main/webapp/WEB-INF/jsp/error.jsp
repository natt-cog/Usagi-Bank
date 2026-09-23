<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="エラー" />
<%@ include file="common/header.jspf" %>
<h1>エラー (<c:out value="${status}" />)</h1>
<p><c:out value="${message}" default="処理中にエラーが発生しました。" /></p>
<p><a href="${ctx}/dashboard">ダッシュボードへ戻る</a></p>
<%@ include file="common/footer.jspf" %>
