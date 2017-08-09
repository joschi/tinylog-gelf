# This project has been deprecated.

tinylog-gelf
============
[![Build Status](https://travis-ci.org/joschi/tinylog-gelf.svg?branch=master)](https://travis-ci.org/joschi/tinylog-gelf)

`tinylog-gelf` is a [Writer](http://www.tinylog.org/javadoc/org/pmw/tinylog/writers/Writer.html) implementation for
[tinylog](http://www.tinylog.org/) writing log messages to a [GELF](http://graylog2.org/gelf) compatible server like
[Graylog2](http://graylog2.org/) or [logstash](http://logstash.net/).


Configuration
-------------

The following configuration settings are supported by `tinylog-gelf`:

* `server` (default: `localhost`)
  * The hostname of the GELF-compatible server.
* `port` (default: `12201`)
  * The port of the GELF-compatible server.
* `transport` (default: `UDP`)
  * The transport protocol to use, valid settings are `UDP` and `TCP`.
* `hostname` (default: local hostname or `localhost` as fallback)
  * The hostname of the application.
* `additionalLogEntryValues` (default: `DATE`, `LEVEL`, `RENDERED_LOG_ENTRY`)
  * Additional information for log messages, see [`LogEntryValue`](http://www.tinylog.org/javadoc/org/pmw/tinylog/writers/LogEntryValue.html).
* `staticFields` (default: empty)
  * Additional static fields for the GELF messages. 

Additional configuration settings are supported by the `GelfWriter` class. Please consult the Javadoc for details.


Examples
--------

`tinylog-gelf` can be configured using a [properties file](http://www.tinylog.org/configuration#file)/system properties
or via the [fluent API](http://www.tinylog.org/configuration#fluent). 

Properties file example:

    tinylog.writer=gelf
    tinylog.writer.server=graylog2.example.com
    tinylog.writer.port=12201
    tinylog.writer.transport=TCP
    tinylog.writer.hostname=myhostname
    tinylog.writer.additionalLogEntryValues=EXCEPTION,FILE,LINE
    tinylog.writer.staticFields=additionalfield1:foo,additionalfield2:bar


Fluent API example:

    GelfWriter gelfWriter = new GelfWriter("graylog2.example.com", 12201, 
                                           GelfTransports.UDP, "myhostname",
                                           EnumSet.of(LogEntryValue.EXCEPTION),
                                           Collections.<String, Object>emptyMap())
    Configurator.defaultConfig()
                .writer(gelfWriter)
                .level(Level.INFO)
                .activate();


Maven Artifacts
---------------

This project is available on Maven Central. To add it to your project simply add the following dependencies to your
`pom.xml`:

    <dependency>
      <groupId>com.github.joschi</groupId>
      <artifactId>tinylog-gelf</artifactId>
      <version>0.2.0</version>
    </dependency>


Support
-------

Please file bug reports and feature requests in [GitHub issues](https://github.com/joschi/tinylog-gelf/issues).


License
-------

Copyright (c) 2014 Jochen Schalanda

This library is licensed under the Apache License, Version 2.0.

See http://www.apache.org/licenses/LICENSE-2.0.html or the LICENSE file in this repository for the full license text.
