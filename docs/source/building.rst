=================================
Building
=================================


*************
Prerequisites
*************

Tupelo is Java code, organized to build using Maven.  Tupelo is a
'multi-module' Maven codebase.  Being Java and using Maven, Tupelo has
two very obvious tool prerequisites:

A 1.7+ version of the Java Development Kit (JDK).  For installation on Ubuntu::

  $ sudo apt-get install openjdk-7-jdk

will install the OpenJDK toolset.  You may prefer the Sun/Oracle
toolset, but that takes more work to install. See
e.g. http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html.

Apache Maven 3.0.5 or greater (earlier 2.* versions may work fine, but
3.0.5 is our current reference). See install instructions at
http://maven.apache.org/download.cgi.  For quick install on Ubuntu::

  $ sudo apt-get install maven

After installation of both tools, run 'mvn -v' which shows Maven's
version and which JDK it has located.  You are hoping to see something
very close to this::

 $ mvn -v

 Apache Maven 3.0.4
 Maven home: /usr/share/maven
 Java version: 1.7.0_65, vendor: Oracle Corporation
 Java home: /usr/lib/jvm/java-7-openjdk-amd64/jre
 Default locale: en_US, platform encoding: UTF-8
 OS name: "linux", version: "3.11.0-15-generic", arch: "amd64", family: "unix"

*************
Dependencies
*************

Tupelo dependencies (i.e. 3rd party code it builds against) include 

* native-lib-loader (loads JNI C code from classpath. Used by fuse4j, tsk4j artifacts below.

  * Available in DIMS Git repositories and Github

* fuse4j (Java-enabled fuse filesystems).  Used by fuse module.

  * Available in DIMS Git repositories and Github

* tsk4j (Java-enabled Sleuthkit disk forensics software).  Used by cli module.

  * Available in DIMS Git repositories and Github

* rabbitmq-log4j-appender (Allow log4j statements to go to RabbitMQ broker). Used by logging module.

  * Bundled locally

These artifacts (jars,poms) are not yet available on public facing
Maven repositories (i.e. Maven Central). These dependencies will need to be installed into the local maven cache
before you can build Tupelo. The easiest way is to clone them and run `mvn package install` to install them.
The native-lib-loader will need to be built/installed first.

The rabbitm1-log4j-adapter is bundled into a project-local Maven repository at ./repository.  The modules
that depend on this (logging) include this local repository in their pom.

*************
Building & Installing
*************

To build::

 $ cd /path/to/tupelo-git-repo
 $ mvn package

will compile and package up all the Java code into what Maven calls
'artifacts', which are just carefully named Java jar files.  You can
alternatively execute::

 $ make package

which uses the local Makefile to invoke Maven. Then::

 $ make install

will take the jars and copy them to /opt/dims/jars, and copy driver
shell scripts from ./bin to /opt/dims/bin.

*************
Unit tests
*************

The above compile/package/install process skips all unit tests.  To
run them (and some can take minutes to complete), we use a Maven
profile called 'tester', like this::

 $ mvn test -Ptester

which will run all the unit tests.  The unit tests for the http/client
sub-module will fail unless you first fire up a 'Tupelo web-based
store', like this (in a different terminal)::

 $ cd /path/to/tupelo-git-repo/http/server
 $ mvn jetty:run

which spawns the Jetty web container to host the Tupelo web-based
store.  The http/client unit tests then access this store via
url base http://localhost:8888/tupelo/

*************
Contact
*************

.. sectionauthor:: Stuart Maclean (@stuart) <stuart @ apl.uw.edu>

