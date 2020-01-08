import Dependencies._

ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.rwk"
ThisBuild / organizationName := "rwk"

lazy val root = (project in file("."))
  .settings(
    name := "VCFotographer",
    // http://logback.qos.ch/
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",
    // https://github.com/lightbend/scala-logging
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    libraryDependencies += scalaTest % Test
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
