ThisBuild / tlBaseVersion := "0.0" // your current series x.y

ThisBuild / organization := "io.chrisdavenport"
ThisBuild / organizationName := "Christopher Davenport"
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("christopherdavenport", "Christopher Davenport")
)
ThisBuild / tlCiReleaseBranches := Seq("main")
ThisBuild / tlSonatypeUseLegacyHost := true


val Scala213 = "2.13.7"

ThisBuild / crossScalaVersions := Seq("2.12.15", Scala213)
ThisBuild / scalaVersion := Scala213

ThisBuild / testFrameworks += new TestFramework("munit.Framework")

val catsV = "2.9.0"
val catsEffectV = "3.4.8"
val fs2V = "3.6.1"
val http4sV = "0.23.18"
val circeV = "0.14.5"
val munitCatsEffectV = "2.0.0-M3"


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

      "com.thesamet.scalapb" %%% "scalapb-runtime" % "0.11.13",


    )
  ).jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule)},
  )

lazy val codeGenerator = project.in(file("codegen/generator"))

lazy val codeGeneratorPlugin = project.in(file("codegen/plugin"))

lazy val codeGeneratorTesting = project.in(file("codegen/testing"))


lazy val site = project.in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .dependsOn(core.jvm)
