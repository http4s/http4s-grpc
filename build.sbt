import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue

inThisBuild(
  Seq(
    crossScalaVersions := Seq(scala213Version, scala3Version),
    scalaVersion := scala213Version,
    tlBaseVersion := "0.3",
    organizationName := "Christopher Davenport",
    startYear := Some(2023),
    licenses := Seq(License.MIT),
    developers := List(
      tlGitHubDev("christopherdavenport", "Christopher Davenport")
    ),
    githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17")),
    githubWorkflowBuildPostamble += WorkflowStep.Sbt(
      List("codeGeneratorPlugin/scripted"),
      name = Some("Scripted tests"),
      cond = Some("matrix.project == 'http4s-grpcJVM' && matrix.java == 'temurin@17'"),
    ),
  )
)

val catsEffectVersion = "3.7.1"
val catsVersion = "2.13.0"
val fs2Version = "3.14.0"
val http4sVersion = "0.23.37"
val munitCatsEffectVersion = "2.2.1"
val sbt2Version = "2.0.0"
val sbtPlatformDepsVersion = "1.0.2"
val sbtProtoc2Version = "1.1.0-RC2"
val sbtProtocVersion = "1.0.8"
val scala212Version = "2.12.21"
val scala213Version = "2.13.18"
val scala3PluginVersion = "3.8.4"
val scala3Version = "3.3.8"
val scalaCheckEffectMunitVersion = "2.1.0"
val scalapbSbt2Version = "1.0.0-alpha.6"
val scalapbVersion = scalapb.compiler.Version.scalapbVersion

lazy val `http4s-grpc` = tlCrossRootProject
  .aggregate(core, codeGenerator, codeGeneratorSbt2, codeGeneratorTesting, codeGeneratorPlugin)
  .settings(unusedCompileDependenciesFilter -= moduleFilter())

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
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
    ),
    unusedCompileDependenciesFilter -= moduleFilter(),
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
  )
  .nativeSettings(
    tlVersionIntroduced := List("2.13", "3").map(_ -> "0.3.0").toMap
  )

lazy val codeGeneratorSettings = Seq(
  name := "http4s-grpc-generator",
  unusedCompileDependenciesFilter -= moduleFilter(),
  headerSources / excludeFilter := HiddenFileFilter || "*Http4sGrpcCodeGenerator.scala" || "*Http4sGrpcServicePrinter.scala",
)

lazy val codeGenerator = project
  .in(file("codegen/generator"))
  .settings(codeGeneratorSettings)
  .settings(
    crossScalaVersions := Seq(scala212Version),
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "compilerplugin" % scalapbVersion
    ),
  )
  .disablePlugins(ScalafixPlugin)

lazy val codeGeneratorSbt2 = project
  .in(file("codegen/generator-sbt2"))
  .settings(codeGeneratorSettings)
  .settings(
    Compile / scalaSource := (codeGenerator / Compile / scalaSource).value,
    scalaVersion := scala3Version,
    crossScalaVersions := Seq(scala3Version),
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "compilerplugin" % scalapbSbt2Version
    ),
    tlVersionIntroduced := Map("3" -> "0.3.1"),
  )
  .disablePlugins(ScalafixPlugin)

lazy val codegenFullName =
  "org.http4s.grpc.generator.Http4sGrpcCodeGenerator"

lazy val codeGeneratorPlugin = project
  .in(file("codegen/plugin"))
  .enablePlugins(BuildInfoPlugin, SbtPlugin)
  .settings(
    name := "sbt-http4s-grpc",
    scalaVersion := scala212Version,
    crossScalaVersions := Seq(scala212Version, scala3PluginVersion),
    pluginCrossBuild / sbtVersion := (scalaBinaryVersion.value match {
      case "2.12" => sbtVersion.value
      case _ => sbt2Version
    }),
    tlJdkRelease := (scalaBinaryVersion.value match {
      case "2.12" => Some(8)
      case _ => Some(17)
    }),
    tlVersionIntroduced := Map("3" -> "0.3.1"),
    tlFatalWarnings := false,
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
    libraryDependencies ++= {
      val sbtV = (pluginCrossBuild / sbtBinaryVersion).value
      val scalaV = (update / scalaBinaryVersion).value
      if (scalaV == "2.12")
        Seq(
          "com.thesamet.scalapb" %% "compilerplugin" % scalapbVersion,
          Defaults.sbtPluginExtra("com.thesamet" % "sbt-protoc" % sbtProtocVersion, sbtV, scalaV),
          Defaults.sbtPluginExtra(
            "org.portable-scala" % "sbt-platform-deps" % sbtPlatformDepsVersion,
            sbtV,
            scalaV,
          ),
        )
      else
        Seq(
          "com.thesamet.scalapb" %% "compilerplugin" % scalapbSbt2Version,
          Defaults.sbtPluginExtra("com.thesamet" % "sbt-protoc" % sbtProtoc2Version, sbtV, scalaV),
        )
    },
    scripted := scripted
      .dependsOn(
        core.jvm / publishLocal,
        codeGenerator / publishLocal,
        codeGeneratorSbt2 / publishLocal,
      )
      .evaluated,
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= Seq(
      "-Xmx1024M",
      s"-Dplugin.version=${version.value}",
      s"-Dscala.version=${(core.jvm / scalaVersion).value}",
    ),
    unusedCompileDependenciesFilter -= moduleFilter(),
    headerSources / excludeFilter := HiddenFileFilter || "*Http4sGrpcPlugin.scala",
  )
  .disablePlugins(ScalafixPlugin)

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
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % http4sVersion % Test,
      "org.http4s" %% "http4s-ember-client" % http4sVersion % Test,
    )
  )
  .nativeSettings(
    tlVersionIntroduced := List("2.13", "3").map(_ -> "0.3.0").toMap
  )
  .disablePlugins(ScalafixPlugin)

lazy val site = project
  .in(file("site"))
  .enablePlugins(Http4sOrgSitePlugin)
  .dependsOn(core.jvm)
