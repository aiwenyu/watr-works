package edu.umass.cs.iesl.watr
package textreflow

import geometry._
import play.api.libs.json._
import Json._
import watrmarks.Label
import data._

import TextReflowF._


trait TextReflowJsonCodecs extends GeometryJsonCodecs with TextReflowBasics {
  import play.api.libs.json._
  import matryoshka._
  import matryoshka.data._
  import matryoshka.implicits._

  def textReflowToJson(textReflow: TextReflow): JsValue = {
    val res = textReflow.cata(attributePara(serializeTextReflow))
    res.toPair._1
  }

  def serializeTextReflow(t: TextReflowF[(TextReflow, JsValue)]): JsValue = t match {
    case Atom(c)                          => obj("a" -> toJson(c))
    case Insert (value)                   => jstr(value)
    case Rewrite ((from, attrJs), to)     => obj("s" -> arr(attrJs, JsString(to.toString)))
    case Bracket (pre, post, (a, attrJs)) => obj("b" -> arr(jstr(pre), attrJs, jstr(post)))
    case Flow(atomsAndattrs)              => toJson(atomsAndattrs.map(_._2))
    case Labeled(labels, (a, attrJs))     => obj("l" -> arr(packLabels(labels), attrJs))
  }

  def packLabels(labels: Set[Label]): JsValue =
    toJson(labels.map(toJson(_)).toList)


  def unpackLabels(js: JsValue): Set[Label] = js match {
    case JsArray(labelJs)  => Set(labelJs.map(_.as[Label]):_*)
    case _ => ???
  }

  def jsonStrToTextReflow(jsStr: String): Option[TextReflow] = {
    jsonToTextReflow(Json.parse(jsStr))
  }

  def jsonToTextReflow(jsValue: JsValue): Option[TextReflow]= {
    val reflow = jsValue
      .ana[TextReflow](unfoldJsonToTextReflow)
      .cata(attributePara(refoldJsonTextReflow))
      .toPair._1

    if (reflow.charAtoms.length > 0) {
      Some(reflow)
    } else None

  }

  def unfoldJsonToTextReflow: Coalgebra[TextReflowF, JsValue] = jsValue => {
    jsValue match {
      case JsArray(jsAttrs) => Flow(jsAttrs.toList)
      case JsString(value) => Insert(value)
      case JsObject(fields) =>
        val field0 = fields.toList.headOption.getOrElse { sys.error("Empty object while unserializing text reflow") }
        field0 match {
          case ("a", charAtomJs)                                          => Atom(charAtomJs.as[CharAtom])
          case ("s", JsArray(Seq(fromJsValue, JsString(toStr))))          => Rewrite(fromJsValue, toStr)
          case ("b", JsArray(Seq(JsString(pre), jsAttr, JsString(post)))) => Bracket(pre, post, jsAttr)
          case ("l", JsArray(Seq(labels, jsAttr)))                        => Labeled(unpackLabels(labels), jsAttr)
          case (_, _)                                                     => sys.error(s"couldn't match JsValue= ${jsValue}")
        }
      case _ => ???
    }
  }

  def refoldJsonTextReflow: GAlgebra[(TextReflow, ?), TextReflowF, TextReflow] = t => fixf(t match {
    case Atom(c)                        => Atom(c)
    case Insert(value)                  => Insert(value)
    case Rewrite ((from, attr), to)     => Rewrite(attr, to)
    case Bracket (pre, post, (a, attr)) => Bracket(pre, post, attr)
    case Flow(atomsAndattrs)            => Flow(atomsAndattrs.map(_._2))
    case Labeled(labels, (a, attr))     => Labeled(labels, attr)
  })

}
