name := """powerapi"""

version := "3.0"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.typesafe" % "config" % "1.2.1",
  "org.apache.logging.log4j" % "log4j-api" % "2.1",
  "org.apache.logging.log4j" % "log4j-core" % "2.1",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test",
  "org.scalatest" %% "scalatest" % "2.2.2" % "test",
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
  "org.jfree" % "jfreechart" % "1.0.19"
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation"
)

parallelExecution in Test := false

instrumentSettings
