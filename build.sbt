ThisBuild / tlBaseVersion := "0.0" // your current series x.y

ThisBuild / organization := "io.chrisdavenport"
ThisBuild / organizationName := "Christopher Davenport"
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("christopherdavenport", "Christopher Davenport")
)
ThisBuild / tlCiReleaseBranches := Seq("main")
ThisBuild / tlSonatypeUseLegacyHost := true

ThisBuild / tlMimaPreviousVersions := Set()

val Scala212 = "2.12.17"
val Scala213 = "2.13.11"

ThisBuild / crossScalaVersions := Seq(Scala213, "3.2.2")
ThisBuild / scalaVersion := Scala213

val catsV = "2.9.0"
val catsEffectV = "3.5.0"
val fs2V = "3.9.2"
val http4sV = "0.23.19"
val munitCatsEffectV = "2.0.0-M3"
import scalapb.compiler.Version.scalapbVersion

// Projects
lazy val `http4s-grpc` = tlCrossRootProject
  .aggregate(core, codeGenerator, codeGeneratorTesting, codeGeneratorPlugin)

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "http4s-grpc",

    libraryDependencies ++= Seq(
      "org.typelevel"               %%% "cats-core"                  % catsV,
      "org.typelevel"               %%% "cats-effect"                % catsEffectV,

      "co.fs2"                      %%% "fs2-core"                   % fs2V,
      "co.fs2"                      %%% "fs2-io"                     % fs2V,
      "co.fs2"                      %%% "fs2-scodec"                 % fs2V,

      "org.http4s"                  %%% "http4s-dsl"                 % http4sV,
      "org.http4s"                  %%% "http4s-ember-server"        % http4sV,
      "org.http4s"                  %%% "http4s-ember-client"        % http4sV,

      "org.typelevel"               %%% "munit-cats-effect"        % munitCatsEffectV         % Test,

      "com.thesamet.scalapb" %%% "scalapb-runtime" % scalapbVersion,

    )
  ).jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule)},
  )

lazy val codeGenerator = project.in(file("codegen/generator")).settings(
  name := "http4s-grpc-generator",
  crossScalaVersions := Seq(Scala212),
  libraryDependencies ++= Seq(
    "com.thesamet.scalapb" %% "compilerplugin" % scalapbVersion
  )
)

lazy val codegenFullName =
  "org.http4s.grpc.generator.Http4sGrpcCodeGenerator"

lazy val codeGeneratorPlugin = project
  .in(file("codegen/plugin"))
  .enablePlugins(BuildInfoPlugin, SbtPlugin)
  .settings(
    name := "sbt-http4s-grpc",
    crossScalaVersions := Seq(Scala212),
    buildInfoPackage := "org.http4s.grpc.sbt",
    buildInfoOptions += BuildInfoOption.PackagePrivate,
    buildInfoKeys := Seq[BuildInfoKey](
      version,
      organization,
      scalaBinaryVersion,
      "codeGeneratorModule" -> (codeGenerator / name).value,
      "coreModule" -> (core.jvm / name).value,
      "codeGeneratorClass" -> codegenFullName,
    ),
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "compilerplugin" % scalapbVersion
    ),
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6"),
    addSbtPlugin("org.portable-scala" % "sbt-platform-deps" % "1.0.2")
  )

lazy val codeGeneratorTesting = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("codegen/testing"))
  .enablePlugins(LocalCodeGenPlugin, BuildInfoPlugin, NoPublishPlugin)
  .dependsOn(core)
  .settings(
    codeGenClasspath := (codeGenerator / Compile / fullClasspath).value,
    Compile / PB.targets := Seq(
      scalapb.gen(grpc = false) -> (Compile / sourceManaged).value / "scalapb",
      genModule(codegenFullName + "$") -> (Compile / sourceManaged).value / "http4s-grpc"
    ),
    Compile / PB.protoSources += baseDirectory.value.getParentFile / "src" / "main" / "protobuf",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %%% "scalapb-runtime" % scalapbVersion % "protobuf",
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectV % Test,
      "org.typelevel" %%% "scalacheck-effect-munit" % "2.0.0-M2" % Test
    ),
    buildInfoPackage := "org.http4s.grpc.e2e.buildinfo",
    buildInfoKeys := Seq[BuildInfoKey]("sourceManaged" -> (Compile / sourceManaged).value / "http4s-grpc"),
    githubWorkflowArtifactUpload := false
  )


lazy val site = project.in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .dependsOn(core.jvm)
