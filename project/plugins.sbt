addSbtPlugin("org.http4s" % "sbt-http4s-org" % "0.18.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.17.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")

addSbtPlugin(
  "com.thesamet" % "sbt-protoc" % "1.0.7"
) // Because sbt-protoc-gen-project brings in 1.0.4
addSbtPlugin("com.thesamet" % "sbt-protoc-gen-project" % "0.1.8")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.17"
