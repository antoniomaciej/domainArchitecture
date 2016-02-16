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

  lazy val Organization = "eu.pmsoft"
  lazy val Name = "domainArchitecture"
  lazy val Version = "0.0.2"

  lazy val eventSourcingApi = (project in file("eventSourcingApi")).
    settings(commonSettings: _*).
    settings(
      name := "event-sourcing-api"
    ).
    settings(apiDependencies: _*).
    settings(testDependenciesOnTest: _*)

  lazy val eventSourcingTest = (project in file("eventSourcingTest")).
    settings(commonSettings: _*).
    settings(
      name := "event-sourcing-test"
    ).
    settings(coreDependencies: _*).
    settings(testDependenciesOnMain: _*).
    dependsOn(eventSourcingApi)


  lazy val eventSourcing = (project in file("eventSourcing")).
    settings(commonSettings: _*).
    settings(
      name := "event-sourcing"
    ).
    settings(coreDependencies: _*).
    dependsOn(eventSourcingTest % "test").
    dependsOn(eventSourcingApi)

  lazy val root = (project in file(".")).
    aggregate(eventSourcing, eventSourcingApi, eventSourcingTest)

  lazy val apiDependencies = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
      "org.scalaz" %% "scalaz-core" % "7.1.2",
      "io.reactivex" % "rxjava" % "1.0.14",
      "joda-time" % "joda-time" % "2.9.2"
    )
  )

  val monocleVersion = "1.1.1"
  lazy val monocleForTest = Seq(
    "com.github.julien-truffaut" %% "monocle-core" % monocleVersion % "test",
    "com.github.julien-truffaut" %% "monocle-generic" % monocleVersion % "test",
    "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion % "test"
  )

  lazy val coreDependencies = Seq(
    libraryDependencies ++= Seq(
      "org.scalikejdbc" %% "scalikejdbc" % "2.3.1",
      "org.scalikejdbc" %% "scalikejdbc-config" % "2.3.1",
      "org.scalaz" %% "scalaz-concurrent" % "7.1.2",
      "com.github.cb372" %% "scalacache-core" % "0.7.5",
      "com.github.cb372" %% "scalacache-caffeine" % "0.7.5",
      "org.scalikejdbc" %% "scalikejdbc-test" % "2.3.1" % "test",
      "org.scala-lang.modules" %% "scala-pickling" % "0.10.1" % "test",
      "com.h2database" % "h2" % "1.4.190" % "test",
      "mysql" % "mysql-connector-java" % "5.1.38" % "test",
      "org.postgresql" % "postgresql" % "9.4-1206-jdbc42" % "test",
      "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
    ) ++ monocleForTest
  )

  lazy val testDependenciesOnMain = Seq(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "scalaz-scalatest" % "0.3.0",
      "org.scalatest" %% "scalatest" % "2.2.6",
      "org.scalacheck" %% "scalacheck" % "1.12.5",
      "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2"
    )
  )
  lazy val testDependenciesOnTest = Seq(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "scalaz-scalatest" % "0.3.0" % "test",
      "org.scalatest" %% "scalatest" % "2.2.6" % "test",
      "org.scalacheck" %% "scalacheck" % "1.12.5" % "test",
      "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % "test"
    )
  )

  lazy val commonSettings = Defaults.coreDefaultSettings ++ Seq(
    organization := Organization,
    version := Version,
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
//    scalacOptions in Compile ++= Seq("-unchecked", "-optimise", "-deprecation", "-feature", "-Yinline-warnings", "-Xlog-implicits"),
    scalacOptions in Compile ++= Seq("-unchecked", "-optimise", "-deprecation", "-feature"),
    resolvers += Classpaths.typesafeReleases,
    publishArtifact in(Test, packageBin) := true,
    publishArtifact in(Test, packageDoc) := true,
    publishArtifact in(Test, packageSrc) := true
  ) ++ Format.settings

}


object Format {

  import com.typesafe.sbt.SbtScalariform._

  lazy val settings = scalariformSettings ++ Seq(
    ScalariformKeys.preferences := formattingPreferences
  )

  lazy val formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences().
      setPreference(AlignParameters, true).
      setPreference(AlignSingleLineCaseStatements, true).
      setPreference(CompactControlReadability, true).
      setPreference(CompactStringConcatenation, true).
      setPreference(DoubleIndentClassDeclaration, true).
      setPreference(FormatXml, true).
      setPreference(IndentLocalDefs, true).
      setPreference(IndentPackageBlocks, true).
      setPreference(IndentSpaces, 2).
      setPreference(MultilineScaladocCommentsStartOnFirstLine, true).
      setPreference(PreserveSpaceBeforeArguments, false).
      setPreference(PreserveDanglingCloseParenthesis, false).
      setPreference(RewriteArrowSymbols, false).
      setPreference(SpaceBeforeColon, false).
      setPreference(SpaceInsideBrackets, false).
      setPreference(SpacesWithinPatternBinders, true)
  }
}
