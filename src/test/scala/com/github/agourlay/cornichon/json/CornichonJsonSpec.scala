package com.github.agourlay.cornichon.json

import io.circe.{ Json, JsonObject }
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ Matchers, WordSpec }
import cats.data.Xor._

class CornichonJsonSpec extends WordSpec with Matchers with PropertyChecks with CornichonJson {

  def refParser(input: String) =
    io.circe.parser.parse(input).fold(e ⇒ throw e, identity)

  def mapToJsonObject(m: Map[String, Json]) =
    Json.fromJsonObject(JsonObject.fromMap(m))

  "CornichonJson" when {
    "parseJson" must {
      "parse Boolean" in {
        forAll { bool: Boolean ⇒
          parseJson(bool) should be(right(Json.fromBoolean(bool)))
        }
      }

      "parse Int" in {
        forAll { int: Int ⇒
          parseJson(int) should be(right(Json.fromInt(int)))
        }
      }

      "parse Long" in {
        forAll { long: Long ⇒
          parseJson(long) should be(right(Json.fromLong(long)))
        }
      }

      "parse Double" in {
        forAll { double: Double ⇒
          parseJson(double) should be(right(Json.fromDoubleOrNull(double)))
        }
      }

      "parse BigDecimal" in {
        forAll { bigDec: BigDecimal ⇒
          parseJson(bigDec) should be(right(Json.fromBigDecimal(bigDec)))
        }
      }

      "parse flat string" in {
        parseJson("cornichon") should be(right(Json.fromString("cornichon")))
      }

      "parse JSON object string" in {
        val expected = mapToJsonObject(Map("name" → Json.fromString("cornichon")))
        parseJson("""{"name":"cornichon"}""") should be(right(expected))
      }

      "parse JSON Array string" in {
        val expected = Json.fromValues(Seq(
          mapToJsonObject(Map("name" → Json.fromString("cornichon"))),
          mapToJsonObject(Map("name" → Json.fromString("scala")))
        ))

        parseJson(
          """
           [
            {"name":"cornichon"},
            {"name":"scala"}
           ]
           """
        ) should be(right(expected))
      }

      "parse data table" in {

        val expected =
          """
            |[
            |{
            |"2LettersName" : false,
            | "Age": 50,
            | "Name": "John"
            |},
            |{
            |"2LettersName" : true,
            | "Age": 11,
            | "Name": "Bob"
            |}
            |]
          """.stripMargin

        parseJson("""
           |  Name  |   Age  | 2LettersName |
           | "John" |   50   |    false     |
           | "Bob"  |   11   |    true      |
         """) should be(right(refParser(expected)))
      }
    }

    "removeFieldsByPath" must {
      "remove root keys" in {
        val input =
          """
            |{
            |"2LettersName" : false,
            | "Age": 50,
            | "Name": "John"
            |}
          """.stripMargin

        val expected =
          """
          |{
          | "Age": 50
          |}
        """.stripMargin
        val paths = Seq("2LettersName", "Name").map(JsonPath.parse)
        removeFieldsByPath(refParser(input), paths) should be(refParser(expected))
      }

      "remove only root keys" in {
        val input =
          """
            |{
            |"name" : "bob",
            |"age": 50,
            |"brothers":[
            |  {
            |    "name" : "john",
            |    "age": 40
            |  }
            |]
            |} """.stripMargin

        val expected = """
           |{
           |"age": 50,
           |"brothers":[
           |  {
           |    "name" : "john",
           |    "age": 40
           |  }
           |]
           |} """.stripMargin

        val paths = Seq("name").map(JsonPath.parse)
        removeFieldsByPath(refParser(input), paths) should be(refParser(expected))
      }

      "remove keys inside specific indexed element" in {
        val input =
          """
            |{
            |"name" : "bob",
            |"age": 50,
            |"brothers":[
            |  {
            |    "name" : "john",
            |    "age": 40
            |  },
            |  {
            |    "name" : "jim",
            |    "age": 30
            |  }
            |]
            |}
          """.stripMargin

        val expected = """
          |{
          |"name" : "bob",
          |"age": 50,
          |"brothers":[
          |  {
          |    "age": 40
          |  },
          |  {
          |    "name" : "jim",
          |    "age": 30
          |  }
          |]
          |} """.stripMargin

        val paths = Seq("brothers[0].name").map(JsonPath.parse)
        removeFieldsByPath(refParser(input), paths) should be(refParser(expected))
      }

      //FIXME - done manually in BodyArrayAssertion for now
      "remove field in each element of a root array" ignore {

        val input =
          """
            |[
            |{
            |  "name" : "bob",
            |  "age": 50
            |},
            |{
            |  "name" : "jim",
            |  "age": 40
            |},
            |{
            |  "name" : "john",
            |  "age": 30
            |}
            |]
          """.stripMargin

        val expected =
          """
            |[
            |{
            |  "name" : "bob"
            |},
            |{
            |  "name" : "jim"
            |},
            |{
            |  "name" : "john"
            |}
            |]
          """.stripMargin

        val paths = Seq("age").map(JsonPath.parse)
        removeFieldsByPath(refParser(input), paths) should be(right(refParser(expected)))
      }

      //FIXME - done manually in BodyArrayAssertion for now
      "remove field in each element of a nested array" ignore {

        val input =
          """
            |{
            |"people":[
            |{
            |  "name" : "bob",
            |  "age": 50
            |},
            |{
            |  "name" : "jim",
            |  "age": 40
            |},
            |{
            |  "name" : "john",
            |  "age": 30
            |}
            |]
            |}
          """.stripMargin

        val expected =
          """
            |{
            |"people":[
            |{
            |  "name" : "bob"
            |},
            |{
            |  "name" : "jim"
            |},
            |{
            |  "name" : "john"
            |}
            |]
            |}
          """.stripMargin

        val paths = Seq("people[*].age").map(JsonPath.parse)
        removeFieldsByPath(refParser(input), paths) should be(right(refParser(expected)))
      }

      "be correct even with duplicate Fields" in {

        val input =
          """
            |{
            |"name" : "bob",
            |"age": 50,
            |"brother":[
            |  {
            |    "name" : "john",
            |    "age": 40
            |  }
            |],
            |"friend":[
            |  {
            |    "name" : "john",
            |    "age": 30
            |  }
            |]
            |}
          """.stripMargin

        val expected =
          """
            |{
            |"name" : "bob",
            |"age": 50,
            |"brother":[
            |  {
            |    "age": 40
            |  }
            |],
            |"friend":[
            |  {
            |    "name" : "john",
            |    "age": 30
            |  }
            |]
            |}
          """.stripMargin

        val paths = Seq("brother[0].name").map(JsonPath.parse)

        removeFieldsByPath(refParser(input), paths) should be(refParser(expected))
      }
    }

    "parseGraphQLJson" must {
      "nominal case" in {
        val in = """
        {
          id: 1
          name: "door"
          items: [
            # pretty broken door
            {state: Open, durability: 0.1465645654675762354763254763343243242}
            null
            {state: Open, durability: 0.5, foo: null}
          ]
        }
        """

        val expected = """
        {
          "id": 1,
          "name": "door",
          "items": [
            {"state": "Open", "durability": 0.1465645654675762354763254763343243242},
            null,
            {"state": "Open", "durability": 0.5, "foo": null}
          ]
        }
        """

        val out = parseGraphQLJson(in)

        out should be(right(refParser(expected)))

      }
    }
  }
}
