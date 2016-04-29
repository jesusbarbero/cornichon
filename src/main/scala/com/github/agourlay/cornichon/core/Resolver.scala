package com.github.agourlay.cornichon.core

import java.util.UUID

import cats.data.Xor
import cats.data.Xor.right
import com.github.agourlay.cornichon.json.JsonPath
import org.parboiled2._
import org.scalacheck.Gen
import org.scalacheck.Gen.Parameters

import scala.util._

class Resolver(extractors: Map[String, Mapper]) {

  val r = new scala.util.Random()

  def findPlaceholders(input: String): List[Placeholder] =
    new PlaceholderParser(input).placeholdersRule.run() match {
      case Failure(e: ParseError) ⇒ List.empty
      case Failure(e: Throwable)  ⇒ throw new ResolverParsingError(e)
      case Success(dt)            ⇒ dt.toList
    }

  def resolvePlaceholder(ph: Placeholder)(session: Session): Xor[CornichonError, String] = ph.key match {
    case "random-uuid"             ⇒ right(UUID.randomUUID().toString)
    case "random-positive-integer" ⇒ right(r.nextInt(1000).toString)
    case "random-string"           ⇒ right(r.nextString(5))
    case "random-boolean"          ⇒ right(r.nextBoolean().toString)
    case "timestamp"               ⇒ right((System.currentTimeMillis / 1000).toString)
    case other: String ⇒
      // Lookup in Session if no custom mapper found
      extractors.get(other).fold[Xor[CornichonError, String]] { session.getXor(other, ph.index) } {
        case SimpleMapper(gen) ⇒
          Xor.catchNonFatal(gen()).leftMap(SimpleMapperError(ph.fullKey, _))
        case GenMapper(gen) ⇒
          Xor.fromOption(gen.apply(Parameters.default), GeneratorError(ph.fullKey))
        case TextMapper(key, transform) ⇒
          session.getXor(key, ph.index).map(transform)
        case JsonMapper(key, jsonPath, transform) ⇒
          session.getXor(key, ph.index).flatMap { sessionValue ⇒
            // No placeholders in JsonMapper for now to avoid people running into infinite recursion
            // Could be enabled if there is a use case for it.
            JsonPath.run(jsonPath, sessionValue)
              .map(_.noSpaces)
              .map(transform)
          }
      }
  }

  def fillPlaceholders(input: String)(session: Session) = {
    def loop(placeholders: List[Placeholder], acc: String): Xor[CornichonError, String] =
      placeholders.headOption.fold[Xor[CornichonError, String]](right(acc)) { ph ⇒
        for {
          resolvedValue ← resolvePlaceholder(ph)(session)
          res ← loop(placeholders.tail, acc.replace(ph.fullKey, resolvedValue))
        } yield res
      }

    loop(findPlaceholders(input), input)
  }

  def fillPlaceholdersUnsafe(input: String)(session: Session): String =
    fillPlaceholders(input)(session).fold(e ⇒ throw e, identity)

  def tuplesResolver(params: Seq[(String, String)], session: Session) = {
    def loop(params: Seq[(String, String)], session: Session, acc: Seq[(String, String)]): Xor[CornichonError, Seq[(String, String)]] =
      params.headOption.fold[Xor[CornichonError, Seq[(String, String)]]](right(acc)) {
        case (name, value) ⇒
          for {
            resolvedName ← fillPlaceholders(name)(session)
            resolvedValue ← fillPlaceholders(value)(session)
            res ← loop(params.tail, session, acc :+ (resolvedName, resolvedValue))
          } yield res
      }

    loop(params, session, Seq.empty[(String, String)])
  }
}

object Resolver {
  def withoutExtractor(): Resolver = new Resolver(Map.empty[String, Mapper])
}

class PlaceholderParser(val input: ParserInput) extends Parser {

  def placeholdersRule = rule {
    Ignore ~ zeroOrMore(PlaceholderRule).separatedBy(Ignore) ~ Ignore ~ EOI
  }

  def PlaceholderRule = rule('<' ~ PlaceholderTXT ~ optIndex ~ '>' ~> Placeholder)

  val notAllowedInKey = "\r\n<>[] "

  def optIndex = rule(optional('[' ~ Number ~ ']'))

  def PlaceholderTXT = rule(capture(oneOrMore(CharPredicate.Visible -- notAllowedInKey)))

  def Ignore = rule { zeroOrMore(!'<' ~ ANY) }

  def Number = rule { capture(Digits) ~> (_.toInt) }

  def Digits = rule { oneOrMore(CharPredicate.Digit) }
}

case class Placeholder(key: String, index: Option[Int]) {
  val fullKey = index.fold(s"<$key>") { index ⇒ s"<$key[$index]>" }
}

sealed trait Mapper

case class SimpleMapper(generator: () ⇒ String) extends Mapper

object SimpleMapper {
  implicit def fromFct(generator: () ⇒ String): SimpleMapper = SimpleMapper(generator)
}

case class GenMapper(gen: Gen[String]) extends Mapper

object GenMapper {
  implicit def fromGen(generator: Gen[String]): GenMapper = GenMapper(generator)
}

case class TextMapper(key: String, transform: String ⇒ String = identity) extends Mapper

case class JsonMapper(key: String, jsonPath: String, transform: String ⇒ String = identity) extends Mapper