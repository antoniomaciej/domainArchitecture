/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Paweł Cesar Sanjuan Szklarz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 *
 */

package eu.pmsoft.domain.minstance

import eu.pmsoft.domain.model.ComponentSpec

import scala.pickling.Defaults._
import scala.pickling.json._

class MicroComponentModelUtilTest extends ComponentSpec {

  //TODO trzeba miec wiele formatow obslugiwanych
  //TODO dodac inny format json dla resta
  //TODO zrobic wykrywanie protokolu po headeras requestu
  it should "serialize case classes to json using pickling" in {
    val example = ExampleApiRequest("name", Some("value"), List(1, 2, 4), NestedTest(3))
    val json = example.pickle
    //    json.value shouldBe ""
    json.unpickle[ExampleApiRequest] should be(example)
  }

  it should "create json from case classes with json4s" in {
    import org.json4s._
    import org.json4s.native.Serialization
    import org.json4s.native.Serialization.write
    implicit val formats = Serialization.formats(NoTypeHints)

    val example = ExampleApiRequest("name", Some("value"), List(1, 2, 4), NestedTest(3))
    val json = write(example)
    json.value shouldBe "{\"name\":\"name\",\"option\":\"value\",\"list\":[1,2,4],\"nested\":{\"nr\":3}}"
  }
  it should "provide a endpoints dynamically" in {

    //TODO tworzyc server http
    //TODO registrowac endpointy na podstawie api podany w klasie, najlepiej bez refleksji
    //TODO dodawac dynamicznie filtry do endpointow

  }
  it should "provide registration information" in {
    //TODO zrobic protokol do informacji o registracji
    //TODO integrowac z eureka

  }
  it should "generate api client" in {
    //TODO generowac implementacje do wywolania api zdalnie
    //TODO bindowac impementacja clienta do infrastruktury registracji
    //TODO wywolac zdalnie http
    //TODO dodac decoratory do klienta, np. histrix
  }
}


