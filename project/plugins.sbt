addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
addSbtPlugin("com.thesamet" % "sbt-protoc-gen-project" % "0.1.8")
// Because sbt-protoc-gen-project brings in 1.0.4
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.8")
addSbtPlugin("org.http4s" % "sbt-http4s-org" % "2.0.5")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.21.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.11")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.20"
