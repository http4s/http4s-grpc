import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue

ThisBuild / tlBaseVersion := "0.1"

ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("christopherdavenport", "Christopher Davenport")
)
ThisBuild / tlCiReleaseBranches := Seq("main")
ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / tlMimaPreviousVersions := Set()

val Scala212 = "2.12.19"
val Scala213 = "2.13.14"

ThisBuild / crossScalaVersions := Seq(Scala213, "3.3.3")
ThisBuild / scalaVersion := Scala213

// disable sbt-header plugin until we are not aligned on the license
ThisBuild / headerCheckAll := Nil

// temporarily disable dependency submissions in CI
ThisBuild / tlCiDependencyGraphJob := false

val catsV = "2.11.0"
val catsEffectV = "3.5.4"
val fs2V = "3.9.2"
val http4sV = "0.23.27"
val munitCatsEffectV = "2.0.0"
import scalapb.compiler.Version.scalapbVersion

// Projects
lazy val `http4s-grpc` = tlCrossRootProject
  .aggregate(core, codeGenerator, codeGeneratorTesting, codeGeneratorPlugin)
  .settings(
    unusedCompileDependenciesFilter -= moduleFilter()
  )
  .disablePlugins(HeaderPlugin)

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "http4s-grpc",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % catsV,
      "org.typelevel" %%% "cats-effect" % catsEffectV,
      "co.fs2" %%% "fs2-core" % fs2V,
      "co.fs2" %%% "fs2-io" % fs2V,
      "co.fs2" %%% "fs2-scodec" % fs2V,
      "org.http4s" %%% "http4s-dsl" % http4sV,
      "org.http4s" %%% "http4s-client" % http4sV,
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectV % Test,
      "com.thesamet.scalapb" %%% "scalapb-runtime" % scalapbVersion,
    ),
    unusedCompileDependenciesFilter -= moduleFilter(),
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
  )
  .disablePlugins(HeaderPlugin)

lazy val codeGenerator =
  project
    .in(file("codegen/generator"))
    .settings(
      name := "http4s-grpc-generator",
      crossScalaVersions := Seq(Scala212),
      libraryDependencies ++= Seq(
        "com.thesamet.scalapb" %% "compilerplugin" % scalapbVersion
      ),
      unusedCompileDependenciesFilter -= moduleFilter(),
    )
    .disablePlugins(HeaderPlugin, ScalafixPlugin)

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
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.7"),
    addSbtPlugin("org.portable-scala" % "sbt-platform-deps" % "1.0.2"),
    unusedCompileDependenciesFilter -= moduleFilter(),
  )
  .disablePlugins(HeaderPlugin, ScalafixPlugin)

lazy val codeGeneratorTesting = crossProject(JVMPlatform, JSPlatform, NativePlatform)
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
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectV % Test,
      "org.typelevel" %%% "scalacheck-effect-munit" % "2.0.0-M2" % Test,
    ),
    buildInfoPackage := "org.http4s.grpc.e2e.buildinfo",
    buildInfoKeys := Seq[BuildInfoKey](
      "sourceManaged" -> (Compile / sourceManaged).value / "http4s-grpc"
    ),
    githubWorkflowArtifactUpload := false,
    unusedCompileDependenciesFilter -= moduleFilter(),
  )
  .disablePlugins(HeaderPlugin, ScalafixPlugin)

lazy val site = project
  .in(file("site"))
  .enablePlugins(Http4sOrgSitePlugin)
  .dependsOn(core.jvm)
