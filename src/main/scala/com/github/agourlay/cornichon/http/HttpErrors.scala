package com.github.agourlay.cornichon.http

import akka.http.scaladsl.model.{ HttpResponse, StatusCode }
import com.github.agourlay.cornichon.core.CornichonError

import scala.util.control.NoStackTrace

sealed trait HttpError extends CornichonError with NoStackTrace

case class TimeoutError(details: String, url: String) extends HttpError {
  val msg = s"HTTP request to '$url' failed with timeout error : '$details'"
}

case class RequestError(e: Throwable, url: String) extends HttpError {
  val msg = s"HTTP request to '$url' failed with ${CornichonError.genStacktrace(e)}"
}

case class ResponseError(e: Throwable, response: HttpResponse) extends HttpError {
  val msg = s"HTTP response '$response' generated error ${CornichonError.genStacktrace(e)}"
}

case class SseError(e: Throwable) extends HttpError {
  val msg = s"expected SSE connection but got ${CornichonError.genStacktrace(e)}"
}

case class WsUpgradeError(status: Int) extends HttpError {
  val msg = s"Websocket upgrade error - status received '$status'"
}

case class StatusNonExpected(expected: StatusCode, response: CornichonHttpResponse) extends HttpError {
  val msg =
    s"""status code expected was '${expected.intValue()}' but '${response.status.intValue()}' was received with body:
       | ${response.body}""".stripMargin
}

case class MalformedHeadersError(error: String) extends CornichonError {
  val msg = s"parsing headers generated error '$error'"
}