addSbtPlugin("org.typelevel" % "sbt-typelevel-ci-release" % "0.6.1")
addSbtPlugin("org.typelevel" % "sbt-typelevel-site" % "0.6.1")
addSbtPlugin("org.typelevel" % "sbt-typelevel-settings" % "0.6.1")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.15.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.16")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6") // Because sbt-protoc-gen-project brings in 1.0.4
addSbtPlugin("com.thesamet" % "sbt-protoc-gen-project" % "0.1.8")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.14"
