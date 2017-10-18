name := "pseudocircuitbreaker"

version := "0.1"

scalaVersion := "2.12.3"

libraryDependencies ++= Seq(
  "org.typelevel"     %% "cats-core"      % "1.0.0-MF" withSources(),
  "org.scala-lang"     % "scala-reflect"  % scalaVersion.value,
  "com.typesafe"       % "config"         % "1.3.0",

  "org.scalatest"     %% "scalatest"      % "3.0.1"                     % "test",
  "org.scalacheck"    %% "scalacheck"     % "1.13.4"                    % "test",
)