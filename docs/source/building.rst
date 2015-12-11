.. _building:

=================================
Building
=================================


**************
Prerequisites
**************

Tupelo is Java code, organized to build using Maven.  Tupelo is a
'multi-module' Maven codebase.  Being Java and using Maven, Tupelo has
two very obvious tool prerequisites:

#. A 1.7+ version of the Java Development Kit (JDK).

   For installation on Ubuntu, install the OpenJDK toolset.

   .. code-block:: none

       $ sudo apt-get install openjdk-7-jdk

   ..


   Alternatively, you may prefer the Sun/Oracle toolset, but that takes more
   work to install. See `Oracle's website`_.

#. Apache Maven 3.0.5 or greater.

   See install instructions on the `Maven website`_.  For quick install on Ubuntu:

   .. code-block:: none

       $ sudo apt-get install maven

   ..
After installation of both tools, run ``mvn -v`` which shows Maven's
version and which JDK it has located.  You are hoping to see something
very close to this:

.. code-block:: none

    $ mvn -v

    Apache Maven 3.0.5
    Maven home: /usr/share/maven
    Java version: 1.7.0_79, vendor: Oracle Corporation
    Java home: /usr/lib/jvm/java-7-openjdk-amd64/jre
    Default locale: en_US, platform encoding: UTF-8
    OS name: "linux", version: "3.19.0-30-generic", arch: "amd64", family: "unix"

..

Also, Tupelo is **platform dependent**. Even though the code is mostly Java, there are portions of native C code.

See the :ref:`native-code` section for more details.

*************
Dependencies
*************

Tupelo dependencies (i.e. 3rd party code it builds against) include 

* ``native-lib-loader`` (loads JNI C code from classpath.)
  Used by fuse4j, tsk4j artifacts below.

  * Available in DIMS Git repositories and Github

* ``fuse4j`` (Java-enabled fuse filesystems).
  Used by fuse module.

  * Available in DIMS Git repositories and Github

* ``tsk4j`` (Java-enabled Sleuthkit disk forensics software).
  Used by cli module.

  * Available in DIMS Git repositories and Github

These artifacts (jars, poms) are not yet available on public facing
Maven repositories (i.e. Maven Central). These dependencies will need to be installed into the local maven cache
before you can build Tupelo. The easiest way is to clone them and run ``mvn package install`` to install them.
The ``native-lib-loader`` will need to be built/installed first.

***************
Property Files
***************

Several parts of Tupelo require a ``filter.properties`` file before they can be built.
This file will contain configuration options that should not be checked into version control,
such as usernames/passwords.

There is a ``template-filter.properties`` file in the root of the repository which contains the options
and a list of where to put the completed file.

.. note::

    The build will fail if you do not provide a ``filter.properties`` file when
    required. At minimum, it should be blank, but it must exist.

..

*********************
Building & Installing
*********************

You can compile and package up all the Java code into what Maven calls
'artifacts', which are just carefully named Java jar files, using:

.. code-block:: none

    $ cd /path/to/tupelo-git-repo
    $ mvn package

..

Alternatively, you can which uses the local ``Makefile`` to invoke Maven, then
take the jars and copy them to ``/opt/dims/jars``, and copy driver shell
scripts from ``./bin`` to ``/opt/dims/bin``, as follows:

.. code-block:: none

    $ make package
    $ make install

..

.. _native-code:

Native Code
-----------

Tupelo has some native code sections which use JNI.
To build the C code, run the following:

.. code-block:: none

    $ mvn compile -Pnative

..

The native code is in the ``model/physical`` sub-project.
After the code is built, the resulting .so files will automatically be copied
to the appropriate folder under ``src/resources``:

.. code-block:: none

    src/resources/edu/uw/apl/tupelo/model/physical/native/Linux/$ARCH/

..

Where ``ARCH`` is ``x86`` or ``x86_64``.

.. note::

    This native code is for Linux only. It is used to get information about the
    disk drives, such as serial number. It will require writing a platform-specific
    version of the code to support OSX or Windows hosts.

..

**********
Unit tests
**********

The above compile/package/install process skips all unit tests.  To
run them (and some can take minutes to complete), we use a Maven
profile called 'tester' to run all the unit tests.

.. code-block:: none

    $ mvn test -Ptester

..

.. TODO(swarner): Still working on this. (Remove todo:: block when done).

.. attention::

    You may get exceptions because HTTP Store can't mount MDFS.

    .. todo::

        Still working on this.

    ..

..


.. _Oracle's website: http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html
.. _Maven website: http://maven.apache.org/download.cgi
