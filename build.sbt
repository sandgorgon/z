val swing = "org.scala-lang.modules" %% "scala-swing" % "3.0.0"
val test = "org.scalatest" %% "scalatest" % "3.2.18" % "test"


lazy val commonSettings = Seq(
  version := "1.0.0",
  scalaVersion := "3.8.2",
  scalacOptions += "-no-indent"
)

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    name := "z",
    libraryDependencies ++= Seq(swing, test),
    assembly / assemblyJarName := "z.jar"
  )
