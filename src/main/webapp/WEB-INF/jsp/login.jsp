<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="UTF-8">
<title>ログイン | うさぎ銀行 勘定系オンライン</title>
<link rel="stylesheet" href="${ctx}/static/css/usagi.css">
</head>
<body class="login">
<div class="login-box">
  <h1>うさぎ銀行<br><small>勘定系オンライン 端末ログイン</small></h1>
  <c:if test="${param.error != null}"><div class="flash ng">ユーザIDまたはパスワードが正しくありません</div></c:if>
  <c:if test="${param.logout != null}"><div class="flash ok">ログアウトしました</div></c:if>
  <form action="${ctx}/login" method="post">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    <label>ユーザID <input type="text" name="username" autofocus required></label>
    <label>パスワード <input type="password" name="password" required></label>
    <button type="submit" class="primary">ログイン</button>
  </form>
  <p class="hint">検証用: teller / teller123, admin / admin123, auditor / audit123</p>
</div>
</body>
</html>
