import NativePackagerKeys._

packageArchetype.java_application

name := """poc-streams-server"""

version := "1.0"

scalaVersion := "2.11.5"

resolvers += "typesafe-repo" at "https://repo.typesafe.com/typesafe/releases"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.10",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.10",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "com.typesafe.akka" %% "akka-stream-experimental" % "1.0-RC1",
  "com.typesafe.akka" %% "akka-http-core-experimental" % "1.0-RC1",
  "com.typesafe.akka" %% "akka-http-scala-experimental" % "1.0-RC1",
  "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.10.5.0.akka23",
  "com.typesafe.play" %% "play-json" % "2.3.5",
  "com.typesafe.play" %% "play-iteratees" % "2.3.5",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "junit" % "junit" % "4.12" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")


fork in run := true
