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

import sbt.Keys._
import sbt._

object domainArchitecture extends Build {

  lazy val Organization = "eu.pmsoft.scala"
  lazy val Name = "domainArchitecture"
  lazy val Version = "0.0.1-SNAPSHOT"

  lazy val commonSettings = Defaults.coreDefaultSettings ++ Seq(
    organization := Organization,
    version := Version,
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 8),
    scalacOptions in Compile ++= Seq("-unchecked", "-optimise", "-deprecation", "-feature"),
    resolvers += Classpaths.typesafeReleases,
    publishArtifact in(Test, packageBin) := true,
    publishArtifact in(Test, packageDoc) := true,
    publishArtifact in(Test, packageSrc) := true
  )

  lazy val eventSourcing = (project in file("eventSourcing")).
    settings(commonSettings: _*).
    settings(dependencies: _*)

  lazy val microInstances = (project in file("microInstances")).
    settings(commonSettings: _*).
    settings(dependencies: _*).
    dependsOn(eventSourcing % "test->test;compile->compile")

  lazy val domainModel = (project in file("domainModel")).
    settings(commonSettings: _*).
    settings(dependencies: _*)

  lazy val securityRoles = (project in file("securityRoles")).
    settings(commonSettings: _*).
    settings(dependencies: _*).
    dependsOn(eventSourcing % "test->test;compile->compile").
    dependsOn(microInstances % "test->test;compile->compile").
    dependsOn(domainModel)

  lazy val passwordReset = (project in file("passwordReset")).
    settings(commonSettings: _*).
    settings(dependencies: _*).
    dependsOn(eventSourcing % "test->test;compile->compile").
    dependsOn(userSession).
    dependsOn(userRegistry).
    dependsOn(microInstances % "test->test;compile->compile").
    dependsOn(domainModel)

  lazy val userRegistry = (project in file("userRegistry")).
    settings(commonSettings: _*).
    settings(dependencies: _*).
    dependsOn(eventSourcing % "test->test;compile->compile").
    dependsOn(securityRoles).
    dependsOn(microInstances % "test->test;compile->compile").
    dependsOn(domainModel)

  lazy val userSession = (project in file("userSession")).
    settings(commonSettings: _*).
    settings(dependencies: _*).
    dependsOn(eventSourcing % "test->test;compile->compile").
    dependsOn(userRegistry).
    dependsOn(microInstances % "test->test;compile->compile").
    dependsOn(domainModel)

  lazy val root = (project in file(".")).
    aggregate(eventSourcing, microInstances,
      domainModel,
      userSession, userRegistry, passwordReset, securityRoles)
  lazy val dependencies = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
      "org.jasypt" % "jasypt" % "1.9.2",
      "com.softwaremill.macwire" %% "runtime" % "1.0.5",
      "com.softwaremill.macwire" %% "macros" % "1.0.5",
      "io.reactivex" %% "rxscala" % "0.25.0",
      "ch.qos.logback" % "logback-classic" % "1.1.2",
      "commons-validator" % "commons-validator" % "1.4.1",
      "com.github.julien-truffaut" %% "monocle-core" % monocleVersion,
      "com.github.julien-truffaut" %% "monocle-generic" % monocleVersion,
      "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion,
      "org.scala-lang.modules" %% "scala-pickling" % "0.10.1",
      "org.json4s" %% "json4s-native" % "3.2.10",
      "com.typesafe.akka" %% "akka-actor" % "2.3.12",
      "io.spray" %% "spray-can" % "1.3.3",
      "io.spray" %% "spray-routing-shapeless2" % "1.3.3",
      "io.spray" %% "spray-io" % "1.3.3",
      "io.spray" %% "spray-servlet" % "1.3.3",
      "org.scalaz" %% "scalaz-core" % "7.1.2",
      "org.scalaz" %% "scalaz-concurrent" % "7.1.2",
      "org.typelevel" %% "scalaz-scalatest" % "0.2.2" % "test",
      "org.scalatest" %% "scalatest" % "2.2.0" % "test",
      "org.scalacheck" %% "scalacheck" % "1.12.4" % "test"
    )
  )
  val monocleVersion = "1.1.1"

}
