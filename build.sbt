name := "sangria-tcp-msgpack-example"
version := "0.1.0-SNAPSHOT"

description := "An example TCP GraphQL server that uses a binary data format (MessagePack)"

scalaVersion := "2.11.8"
scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria" % "0.6.0",
  "org.sangria-graphql" %% "sangria-msgpack" % "0.1.0",

  "com.typesafe.akka" %% "akka-stream-experimental" % "2.0.3"
)

Revolver.settings