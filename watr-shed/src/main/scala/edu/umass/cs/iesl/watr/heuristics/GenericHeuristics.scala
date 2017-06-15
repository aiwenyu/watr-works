package edu.umass.cs.iesl.watr

package heuristics

import edu.umass.cs.iesl.watr.textreflow.data.TextReflow
import edu.umass.cs.iesl.watr.textreflow.data._

import util.control.Breaks._
import Constants._
import Utils._
import TypeTags._
import edu.umass.cs.iesl.watr.geometry.{CharAtom, LTBounds}

import scala.collection.mutable.ListBuffer

object GenericHeuristics {

    def tokenizeTextReflow(textReflow: TextReflow): ListBuffer[String] = {
        val tokens: ListBuffer[String] = ListBuffer[String]()
        val currentToken: ListBuffer[String] = ListBuffer[String]()

        var prevCharPosition: Int = -1
        val yPosition: Int = getYPosition(textReflow = textReflow)

        for (charAtom <- textReflow.charAtoms()) {
            val currentCharacter = charAtom.char
            if (charAtom.bbox.top.asInstanceOf[Int].==(yPosition)){
                if (prevCharPosition > 0) {
                    val spaceBetweenChars = charAtom.bbox.left.asInstanceOf[Int] - prevCharPosition
                    if (spaceBetweenChars < SPACE_BETWEEN_WORDS_THRESHOLD) {
                        if (charAtom.bbox.top.==(yPosition)) {
                            currentToken += currentCharacter
                        }
                        else {
                            if (charAtom.bbox.right.asInstanceOf[Int] > prevCharPosition) {
                                tokens += currentToken.mkString
                                currentToken.clear()
                            }
                            else {
                                currentToken += currentCharacter
                            }
                        }

                    }
                    else if (spaceBetweenChars >= SPACE_BETWEEN_WORDS_THRESHOLD) {
                        tokens += currentToken.mkString
                        currentToken.clear()
                        if (charAtom.bbox.top.==(yPosition)) {
                            currentToken += currentCharacter
                        }
                    }
                }
                if(prevCharPosition.==(-1)){
                    currentToken += currentCharacter
                }
                prevCharPosition = charAtom.bbox.right.asInstanceOf[Int]
            }
            else if (charAtom.bbox.right.asInstanceOf[Int] < prevCharPosition){
                currentToken += currentCharacter
            }
        }

        if (currentToken.nonEmpty) {
            tokens += currentToken.mkString
            currentToken.clear()
        }

        tokens.filter(_.nonEmpty)
    }

    def getSeparateComponentsByText(tokenizedTextReflow: ListBuffer[String]): ListBuffer[String] = {

        val separateComponents: ListBuffer[String] = ListBuffer[String]()
        val separateComponent: ListBuffer[String] = ListBuffer[String]()

        for (textReflowToken <- tokenizedTextReflow) {
            breakable {
                if (WORD_SEPARATORS.contains(textReflowToken.toLowerCase)) {
                    separateComponents += separateComponent.mkString(SPACE_SEPARATOR)
                    separateComponent.clear()
                }
                else if( PUNCTUATION_SEPARATORS.contains(textReflowToken.takeRight(n = 1))){
                    separateComponent += textReflowToken.dropRight(n = 1)
                    separateComponents += separateComponent.mkString(SPACE_SEPARATOR)
                    separateComponent.clear()
                }
                else {
                    separateComponent += textReflowToken
                }
            }
        }

        if (separateComponent.nonEmpty) {
            separateComponents += separateComponent.mkString(SPACE_SEPARATOR)
        }
        separateComponents.filter(_.nonEmpty)
    }

    def getSeparateComponentsByGeometry(componentsSeparatedByText: ListBuffer[String], textReflow: TextReflow): ListBuffer[String] = {

        val separateComponents: ListBuffer[String] = ListBuffer[String]()
        val separateComponent: ListBuffer[String] = ListBuffer[String]()

        var currentComponentIndex: Int = 0
        var currentComponent: String = componentsSeparatedByText(currentComponentIndex)
        var currentSeparateComponentIndex: Int = 0
        var currentSeparateComponent: String = currentComponent.split(SPACE_SEPARATOR)(currentSeparateComponentIndex)

        var prevCharPosition: Int = -1
        var usualSpaceWidth: Int = 0
        var currentCharAtomIndex: Int = 0
        val yPosition: Int = getYPosition(textReflow = textReflow)

        while (currentCharAtomIndex < textReflow.charAtoms().length && currentComponentIndex < componentsSeparatedByText.length) {
            var charAtom: CharAtom = textReflow.charAtoms()(currentCharAtomIndex)
            currentComponent = componentsSeparatedByText(currentComponentIndex)
            currentSeparateComponent = currentComponent.split(SPACE_SEPARATOR)(currentSeparateComponentIndex)
            val currentCharacter = charAtom.char.toCharArray.head
            if (currentCharacter.equals(currentSeparateComponent.head)) {
                if (prevCharPosition < 0) {
                    separateComponent += currentSeparateComponent
                }
                else {
                    if (usualSpaceWidth == 0) {
                        usualSpaceWidth = charAtom.bbox.left.asInstanceOf[Int] - prevCharPosition
                    }
                    else if ((charAtom.bbox.left.asInstanceOf[Int] - prevCharPosition) > 2 * usualSpaceWidth) {
                        separateComponents += separateComponent.mkString(SPACE_SEPARATOR)
                        separateComponent.clear()
                    }
                    separateComponent += currentSeparateComponent
                    if (currentSeparateComponentIndex + 1 == currentComponent.split(SPACE_SEPARATOR).length) {
                        separateComponents += separateComponent.mkString(SPACE_SEPARATOR)
                        separateComponent.clear()
                    }
                }

                val indices = getNextComponentIndices(currentSeparateComponentIndex, currentComponentIndex, currentComponent)
                currentSeparateComponentIndex = indices._1
                currentComponentIndex = indices._2

            }
            charAtom = textReflow.charAtoms()(currentCharAtomIndex)
            if (charAtom.bbox.top.asInstanceOf[Int].==(yPosition) || (charAtom.bbox.right.asInstanceOf[Int] < textReflow.charAtoms()(currentCharAtomIndex - 1).bbox.right.asInstanceOf[Int]
                                                                        && textReflow.charAtoms()(currentCharAtomIndex - 1).bbox.top.asInstanceOf[Int].==(yPosition))) {
                prevCharPosition = charAtom.bbox.right.asInstanceOf[Int]
            }
            currentCharAtomIndex += 1

        }


        if (separateComponents.nonEmpty) {
            return separateComponents.filter(_.nonEmpty)
        }
        componentsSeparatedByText
    }

}
