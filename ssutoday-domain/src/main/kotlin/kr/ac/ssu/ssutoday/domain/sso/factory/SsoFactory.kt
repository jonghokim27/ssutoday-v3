package kr.ac.ssu.ssutoday.domain.sso.factory

import kr.ac.ssu.ssutoday.domain.sso.SsoClient
import kr.ac.ssu.ssutoday.domain.sso.SsoClientView
import kr.ac.ssu.ssutoday.domain.sso.SsoToken
import kr.ac.ssu.ssutoday.domain.sso.SsoTokenView

fun SsoClient.toView() = SsoClientView(id, callbackUrl)

fun SsoToken.toView() = SsoTokenView(token, clientId, studentId, name, major)
