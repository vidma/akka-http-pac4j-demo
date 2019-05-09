lazy val akkaHttpVersion = "10.0.11"
lazy val akkaVersion    = "2.5.8"

lazy val akkaHttpPac4jVersion = "0.5.0-SNAPSHOT" // FIXME
lazy val pac4jVersion = "3.6.1"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "org.pac4j.examples",
      scalaVersion    := "2.11.7"
    )),
    name := "akka-http-pac4j-demo",
    libraryDependencies ++= Seq(
      "com.stackstate" %% "akka-http-pac4j"         % akkaHttpPac4jVersion,
      // "org.pac4j" % "pac4j"  % pac4jVersion,
      // "org.pac4j" % "pac4j-core" % pac4jVersion,
      "org.pac4j" % "pac4j-jwt" % pac4jVersion exclude("commons-io" , "commons-io"),
      //   "org.pac4j" % "pac4j-sql" % "2.1.0",
      "org.pac4j" % "pac4j-http" % pac4jVersion,
      "org.pac4j" % "pac4j-ldap" % pac4jVersion,
      "org.pac4j" % "pac4j-saml" % pac4jVersion,

      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml"        % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"          % akkaVersion,

      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit"         % akkaVersion     % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"            % "3.0.5"         % Test
    )
  )
