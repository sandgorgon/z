val actors = "org.scala-lang" % "scala-actors" % "2.11.7"
val swing = "org.scala-lang" % "scala-swing" % "2.11.0-M7"
val test = "org.scalatest" %% "scalatest" % "2.2.4" % "test"


lazy val commonSettings = Seq(
  version := "1.0.0",
  scalaVersion := "2.11.6"
)

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    name := "z",
    libraryDependencies ++= Seq(swing, actors, test),
    assemblyJarName in assembly := "z.jar"
  )

// Uncomment to use Akka
//libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.11"

