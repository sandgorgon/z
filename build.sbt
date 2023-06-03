val actors = "org.scala-lang" % "scala-actors" % "2.11.7"
//val actors = "com.typesafe.akka" %% "akka-actor" % "2.3.11"
val swing = "org.scala-lang" % "scala-swing" % "2.11.0-M7"
val test = "org.scalatest" %% "scalatest" % "3.0.8" % Test 


lazy val commonSettings = Seq(
  version := "1.0.0",
  scalaHome := Some(file("/home/sandgorgon/.sdkman/candidates/scala/current")),
)

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    name := "z",
    libraryDependencies ++= Seq(swing, actors, test), 
    assemblyJarName in assembly := "z.jar"
  )

