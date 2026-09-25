scalaVersion := sys.props("scala.version")

enablePlugins(Http4sGrpcPlugin)

Compile / PB.targets ++= Seq[protocbridge.Target](
  scalapb.gen(grpc = false) -> (Compile / sourceManaged).value / "scalapb"
)

// on sbt 2 the ScalaPB 1.0 runtime evicts the 0.11 runtime core depends on
TaskKey[Unit]("checkRuntime") := {
  val revisions = (Compile / update).value.allModules.collect {
    case module if module.name.startsWith("scalapb-runtime") => module.revision
  }

  assert(
    revisions == Seq(scalapb.compiler.Version.scalapbVersion),
    s"resolved scalapb-runtime $revisions",
  )
}

TaskKey[Unit]("checkGenerated") := {
  def allFiles(file: File): Vector[File] =
    if (file.isDirectory) IO.listFiles(file).toVector.flatMap(allFiles)
    else Vector(file)

  val generated = allFiles((Compile / http4sGrpcOutputPath).value)
  assert(
    generated.exists(_.getName == "TestService.scala"),
    s"http4s-grpc generated no TestService.scala, only: $generated",
  )
}
