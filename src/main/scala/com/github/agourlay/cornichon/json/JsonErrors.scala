package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.CornichonError

sealed trait JsonError extends CornichonError

case class NotAnArrayError[A](badPayload: A) extends JsonError {
  val msg = s"expected JSON Array but got $badPayload"
}

case class MalformedJsonError[A](input: A, message: String) extends JsonError {
  val msg = s"malformed JSON input $input: $message"
}

case class MalformedGraphQLJsonError[A](input: A, exception: Throwable) extends JsonError {
  val msg = s"malformed GraphQLJSON input $input with ${exception.getMessage}"
}

case class JsonPathParsingError(input: String, error: String) extends JsonError {
  val msg = s"error thrown during JsonPath parsing for input '$input' : $error"
}

case class JsonPathError(input: String, error: Throwable) extends JsonError {
  val msg = s"error thrown during JsonPath parsing for input '$input' : ${error.getMessage}"
}

case class WhiteListError(msg: String) extends CornichonError

