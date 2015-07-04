name := """z"""

version := "1.0"

scalaVersion := "2.11.6"

// Change this to another test framework if you prefer
libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test"
libraryDependencies += "org.scala-lang" % "scala-actors" % "2.11.7"
libraryDependencies += "org.scala-lang" % "scala-swing" % "2.11.0-M7"
libraryDependencies += "org.scala-lang" % "scala-xml" % "2.11.0-M4"

assemblyJarName in assembly := "z.jar"

// Uncomment to use Akka
//libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.11"

