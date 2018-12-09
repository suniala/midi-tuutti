lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      scalaVersion := "2.12.8",
      version := "0.1"
    )),
    name := "midi-tuutti",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.1" % Test,
      "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % Test
    )
  )
