import NativePackagerKeys._

packageArchetype.java_application

name := """poc-streams-server"""

version := "1.0"

scalaVersion := "2.11.5"

resolvers += "typesafe-repo" at "https://repo.typesafe.com/typesafe/releases"

libraryDependencies ++= {
  val akkaV       = "2.3.10"
  val akkaStreamV = "1.0-RC2"
  val playV       = "2.4.0-RC5"  //"2.3.5"

  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV,
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-scala-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamV,
    "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.10.5.0.akka23",
    "com.typesafe.play" %% "play-json" % playV,
    "com.typesafe.play" %% "play-iteratees" % playV,
    "ch.qos.logback" % "logback-classic" % "1.1.2",
    "junit" % "junit" % "4.12" % "test",
    "com.novocode" % "junit-interface" % "0.11" % "test"
  )
}
testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")


fork in run := true
