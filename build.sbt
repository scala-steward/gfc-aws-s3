name        := "gfc-aws-s3"

organization  := "com.gilt"

description := "Library to handle data streaming to and from s3"

scalaVersion  := "2.12.4"

crossScalaVersions := Seq(scalaVersion.value, "2.11.11")

scalacOptions += "-target:jvm-1.7"

javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

libraryDependencies ++= Seq(
  "org.slf4j"          % "slf4j-api"        % "1.7.25",
  "com.amazonaws"      %  "aws-java-sdk-s3" % "1.11.408",
  "com.typesafe.akka"  %% "akka-stream"     % "2.5.6",

  "org.scalatest"     %% "scalatest"                   % "3.0.5" % Test,
  "org.scalamock"     %% "scalamock-scalatest-support" % "3.6.0" % Test
)


releaseCrossBuild := true

releasePublishArtifactsAction := PgpKeys.publishSigned.value

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

startYear := Some(2017)

licenses := Seq("Apache-style" -> url("https://raw.githubusercontent.com/gilt/gfc-aws-s3/master/LICENSE"))

homepage := Some(url("https://github.com/gilt/gfc-aws-s3"))

pomExtra := (
  <scm>
    <url>https://github.com/gilt/gfc-aws-s3.git</url>
    <connection>scm:git:git@github.com:gilt/gfc-aws-s3.git</connection>
  </scm>
    <developers>
      <developer>
        <id>mikegirkin</id>
        <name>Mike Girkin</name>
        <url>https://github.com/mikegirkin</url>
      </developer>
    </developers>
  )
