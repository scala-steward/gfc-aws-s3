import scoverage.ScoverageKeys

name        := "gfc-aws-s3"

organization  := "org.gfccollective"

description := "Library to handle data streaming to and from s3"

scalaVersion  := "2.13.7"

crossScalaVersions := Seq(scalaVersion.value, "2.12.15")

scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

ScoverageKeys.coverageFailOnMinimum := true

ScoverageKeys.coverageMinimumStmtTotal := 90.0

libraryDependencies ++= Seq(
  "org.slf4j"          % "slf4j-api"        % "1.7.32",
  "com.amazonaws"      %  "aws-java-sdk-s3" % "1.12.131",
  "com.typesafe.akka" %% "akka-stream"      % "2.6.18",
  "org.scalatest"     %% "scalatest"        % "3.2.10" % Test,
  "org.scalamock"     %% "scalamock"        % "5.1.0" % Test,
  "com.adobe.testing"  % "s3mock-testcontainers"  % "2.3.2" % Test,
  "org.slf4j"          % "slf4j-simple"        % "1.7.32" % Test,
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.6.0" % Test,
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

Test / publishArtifact := false

pomIncludeRepository := { _ => false }

startYear := Some(2017)

licenses := Seq("Apache-style" -> url("https://raw.githubusercontent.com/gfc-collective/gfc-aws-s3/main/LICENSE"))

homepage := Some(url("https://github.com/gfc-collective/gfc-aws-s3"))

pomExtra := (
  <scm>
    <url>https://github.com/gfc-collective/gfc-aws-s3.git</url>
    <connection>scm:git:git@github.com:gfc-collective/gfc-aws-s3.git</connection>
  </scm>
    <developers>
      <developer>
        <id>mikegirkin</id>
        <name>Mike Girkin</name>
        <url>https://github.com/mikegirkin</url>
      </developer>
    </developers>
  )
