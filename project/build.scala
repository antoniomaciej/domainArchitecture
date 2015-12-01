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
  lazy val Version = "0.0.1-SNAPSHOT"

  lazy val eventSourcingApi = (project in file("eventSourcingApi")).
    settings(commonSettings: _*).
    settings(testDependencies: _*).
    settings(dependencies: _*)

  lazy val eventSourcingTest = (project in file("eventSourcingTest")).
    settings(commonSettings: _*).
    settings(dependencies: _*).
    settings(testDependencies: _*).
    dependsOn(eventSourcingApi)

  //coverage is not well calculated, so disable integration tests.
//  lazy val FunTest = config("fun") extend (Test)

  def itFilter(name: String): Boolean = name endsWith "IntegrationTest"

  def unitFilter(name: String): Boolean = (name endsWith "Test") && !itFilter(name)

  lazy val eventSourcing = (project in file("eventSourcing")).
//    configs(FunTest).
//    settings(inConfig(FunTest)(Defaults.testTasks): _*).
    settings(
      testOptions in Test ++= Seq(Tests.Filter(unitFilter))
//      testOptions in FunTest := Seq(Tests.Filter(itFilter))
    ).
    settings(commonSettings: _*).
    settings(dependencies: _*).
    dependsOn(eventSourcingTest % "test").
    dependsOn(eventSourcingApi)

  lazy val domainModel = (project in file("domainModel")).
    settings(commonSettings: _*).
    settings(dependencies: _*)

  lazy val securityRoles = (project in file("securityRoles")).
    settings(commonSettings: _*).
    settings(dependencies: _*).
    dependsOn(eventSourcing).
    dependsOn(eventSourcingTest % "test").
    dependsOn(domainModel)

  lazy val passwordReset = (project in file("passwordReset")).
    settings(commonSettings: _*).
    settings(dependencies: _*).
    dependsOn(eventSourcing).
    dependsOn(eventSourcingTest % "test").
    dependsOn(domainModel).
    dependsOn(userSession, userRegistry)

  lazy val userRegistry = (project in file("userRegistry")).
    settings(commonSettings: _*).
    settings(dependencies: _*).
    dependsOn(eventSourcing).
    dependsOn(eventSourcingTest % "test").
    dependsOn(domainModel).
    dependsOn(securityRoles)

  lazy val userSession = (project in file("userSession")).
    settings(commonSettings: _*).
    settings(dependencies: _*).
    dependsOn(eventSourcing).
    dependsOn(eventSourcingTest % "test").
    dependsOn(domainModel).
    dependsOn(userRegistry)

  lazy val deploymentApp = (project in file("deploymentApp")).
    settings(commonSettings: _*).
    settings(dependencies: _*).
    dependsOn(eventSourcingTest % "test").
    settings(deploymentDependencies: _*).
    dependsOn(domainModel).
    dependsOn(userSession, userRegistry, passwordReset, securityRoles)

  //    configs(FunTest).
  //    settings(inConfig(FunTest)(Defaults.testSettings): _*).
  lazy val root = (project in file(".")).
    aggregate(eventSourcing, eventSourcingApi, eventSourcingTest,
      domainModel,
      userSession, userRegistry, passwordReset, securityRoles,
      deploymentApp)

  val monocleVersion = "1.1.1"

  lazy val dependencies = Seq(
    libraryDependencies ++= Seq(
      "org.scalikejdbc" %% "scalikejdbc" % "2.3.1",
      "org.scalikejdbc" %% "scalikejdbc-config" % "2.3.1",
      "org.scalikejdbc" %% "scalikejdbc-test" % "2.3.1" % "test",
      "com.h2database" % "h2" % "1.4.190" % "test",
      "mysql" % "mysql-connector-java" % "5.1.38" % "test",
      "org.postgresql" % "postgresql" % "9.4-1206-jdbc42" % "test",
      "org.jasypt" % "jasypt" % "1.9.2",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
      "ch.qos.logback" % "logback-classic" % "1.1.3",
      "com.softwaremill.macwire" %% "runtime" % "1.0.5",
      "com.softwaremill.macwire" %% "macros" % "1.0.5",
      "commons-validator" % "commons-validator" % "1.4.1",
      "com.github.julien-truffaut" %% "monocle-core" % monocleVersion,
      "com.github.julien-truffaut" %% "monocle-generic" % monocleVersion,
      "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion,
      "org.scalaz" %% "scalaz-core" % "7.1.2",
      "org.scalaz" %% "scalaz-concurrent" % "7.1.2",
      "io.reactivex" % "rxjava" % "1.0.14",
      "com.google.guava" % "guava" % "19.0",
      "org.mapdb" % "mapdb" % "2.0-beta12",
      "org.scala-lang.modules" %% "scala-pickling" % "0.11.0"
    )
  )

  lazy val testDependencies = Seq(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "scalaz-scalatest" % "0.2.2",
      "org.scalatest" %% "scalatest" % "2.2.0",
      "org.scalacheck" %% "scalacheck" % "1.12.4",
      "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2"
    )
  )

  lazy val akkaVersion = "2.3.12"
  lazy val sprayVersion = "1.3.3"

  lazy val deploymentDependencies = Seq(
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-native" % "3.2.10",
      "com.mchange" % "c3p0" % "0.9.5.1",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "io.spray" %% "spray-can" % sprayVersion,
      "io.spray" %% "spray-routing-shapeless2" % sprayVersion,
      "io.spray" %% "spray-testkit" % sprayVersion % "test",
      "io.spray" %% "spray-client" % sprayVersion % "test",
      "ch.qos.logback" % "logback-classic" % "1.1.2",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
    )
  )


  lazy val commonSettings = Defaults.coreDefaultSettings ++ Seq(
    organization := Organization,
    version := Version,
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
    scalacOptions in Compile ++= Seq("-unchecked", "-optimise", "-deprecation", "-feature", "-Yinline-warnings"),
    resolvers += Classpaths.typesafeReleases,
    resolvers += Resolver.file("Local repo", file(System.getProperty("user.home") + "/.ivy2/local"))(Resolver.ivyStylePatterns),
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
