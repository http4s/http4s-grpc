import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue

inThisBuild(
  Seq(
    crossScalaVersions := Seq(scala213Version, scala3Version),
    scalaVersion := scala213Version,
    tlBaseVersion := "0.2",
    organizationName := "Christopher Davenport",
    startYear := Some(2023),
    licenses := Seq(License.MIT),
    developers := List(
      tlGitHubDev("christopherdavenport", "Christopher Davenport")
    ),
  )
)

val catsEffectVersion = "3.6.3"
val catsVersion = "2.13.0"
val fs2Version = "3.12.2"
val http4sVersion = "0.23.33"
val munitCatsEffectVersion = "2.1.0"
val sbtPlatformDepsVersion = "1.0.2"
val sbtProtocVersion = "1.0.8"
val scala212Version = "2.12.21"
val scala213Version = "2.13.18"
val scala3Version = "3.3.7"
val scalaCheckEffectMunitVersion = "2.1.0-RC1"
val scalapbGoogleProtosVersion = "2.9.6-0"
val scalapbVersion = scalapb.compiler.Version.scalapbVersion

lazy val `http4s-grpc` = tlCrossRootProject
  .aggregate(core, codeGenerator, codeGeneratorTesting, codeGeneratorPlugin)
  .settings(unusedCompileDependenciesFilter -= moduleFilter())

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "http4s-grpc",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % catsVersion,
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
      "co.fs2" %%% "fs2-core" % fs2Version,
      "co.fs2" %%% "fs2-io" % fs2Version,
      "co.fs2" %%% "fs2-scodec" % fs2Version,
      "org.http4s" %%% "http4s-dsl" % http4sVersion,
      "org.http4s" %%% "http4s-client" % http4sVersion,
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectVersion % Test,
      "com.thesamet.scalapb" %%% "scalapb-runtime" % scalapbVersion,
      "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % scalapbGoogleProtosVersion,
    ),
    unusedCompileDependenciesFilter -= moduleFilter(),
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
  )

lazy val codeGenerator =
  project
    .in(file("codegen/generator"))
    .settings(
      name := "http4s-grpc-generator",
      crossScalaVersions := Seq(scala212Version),
      libraryDependencies ++= Seq(
        "com.thesamet.scalapb" %% "compilerplugin" % scalapbVersion
      ),
      unusedCompileDependenciesFilter -= moduleFilter(),
      headerSources / excludeFilter := HiddenFileFilter || "*Http4sGrpcCodeGenerator.scala" || "*Http4sGrpcServicePrinter.scala",
    )
    .disablePlugins(ScalafixPlugin)

lazy val codegenFullName =
  "org.http4s.grpc.generator.Http4sGrpcCodeGenerator"

lazy val codeGeneratorPlugin = project
  .in(file("codegen/plugin"))
  .enablePlugins(BuildInfoPlugin, SbtPlugin)
  .settings(
    name := "sbt-http4s-grpc",
    crossScalaVersions := Seq(scala212Version),
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
    addSbtPlugin("com.thesamet" % "sbt-protoc" % sbtProtocVersion),
    addSbtPlugin("org.portable-scala" % "sbt-platform-deps" % sbtPlatformDepsVersion),
    unusedCompileDependenciesFilter -= moduleFilter(),
    headerSources / excludeFilter := HiddenFileFilter || "*Http4sGrpcPlugin.scala",
  )
  .disablePlugins(ScalafixPlugin)

lazy val codeGeneratorTesting = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("codegen/testing"))
  .enablePlugins(LocalCodeGenPlugin, BuildInfoPlugin, NoPublishPlugin)
  .dependsOn(core)
  .settings(
    tlFatalWarnings := false,
    codeGenClasspath := (codeGenerator / Compile / fullClasspath).value,
    Compile / PB.targets := Seq(
      scalapb.gen(grpc = false) -> (Compile / sourceManaged).value / "scalapb",
      genModule(codegenFullName + "$") -> (Compile / sourceManaged).value / "http4s-grpc",
    ),
    Compile / PB.protoSources += baseDirectory.value.getParentFile / "src" / "main" / "protobuf",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %%% "scalapb-runtime" % scalapbVersion % "protobuf",
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectVersion % Test,
      "org.typelevel" %%% "scalacheck-effect-munit" % scalaCheckEffectMunitVersion % Test,
    ),
    buildInfoPackage := "org.http4s.grpc.e2e.buildinfo",
    buildInfoKeys := Seq[BuildInfoKey](
      "sourceManaged" -> (Compile / sourceManaged).value / "http4s-grpc"
    ),
    githubWorkflowArtifactUpload := false,
    unusedCompileDependenciesFilter -= moduleFilter(),
  )
  .disablePlugins(ScalafixPlugin)

lazy val site = project
  .in(file("site"))
  .enablePlugins(Http4sOrgSitePlugin)
  .dependsOn(core.jvm)
