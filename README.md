<img src="framework-docs/src/docs/pipelite-framework.svg" width="200"> 

# Pipelite Framework

This is the home of the Pipelite Framework.

Pipelite is a Java&trade; framework that, through the use of the _[Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com)_, simplifies and speeds up the development of message driven asynchronous applications.

It supports the interaction with the external systems by the declarative use of the _channel-adapters_ that provide an high level of abstraction for the third party technologies.

## Code of Conduct

This project has adopted the [Contributor Covenant](CODE_OF_CONDUCT.md) as its code of conduct.
By participating, you are expected to uphold it.

Want to contribute? See [CONTRIBUTING.md](CONTRIBUTING.md) for how to report issues, propose
changes, and the conventions this repository follows.

## Documentation

The full reference documentation, including getting-started guides and the DSL reference, is
published at [pipelite.io/docs/reference](https://www.pipelite.io/docs/reference).

## Build from Source

Requirements: JDK 17 and a network connection for the first build (dependencies are fetched from
Maven Central). A wrapper is included, so a local Maven install isn't required.

```bash
git clone https://github.com/pipelite-projects/pipelite-framework.git
cd pipelite-framework
./mvnw clean verify
```

This builds every module and runs the full test suite. To build without running tests:

```bash
./mvnw clean install -DskipTests
```

## License

The Pipelite Framework is released under the version 2.0 of the [Apache License](https://www.apache.org/licenses/LICENSE-2.0).

Java&trade;, Java&trade; SE, Java&trade; EE, and OpenJDK&trade; are trademarks of Oracle and/or its affiliates.

Spring&#174; is a registered trademark of Pivotal Software, Inc. and/or its affiliates. 

Apache&trade;, Apache Kafka&trade;, are trademarks or registered trademarks of the Apache Software Foundation in the United States and/or other countries. 

Undertow&trade; is a trademark of Red Hat, Inc. in the United States and other countries.

All other trademarks and copyrights are property of their respective owners and are only mentioned for informative purposes. Other names may be trademarks of their respective owners.