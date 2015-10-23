=====================
Installing & Running
=====================

*************
Tupelo Shell
*************

The shell is the primary means of interacting with Tupelo.
The shell has can be used to analyze the files on a physical disk or image,
send a disk or image to either a local or remote store,
check a store for files with a specific MD5 hash and more.

---------------------
Installing the Shell
---------------------

The Tupelo shell can be installed by either running ``make install`` in the root of the Tupelo repository,
or running the ``install-shell.sh`` script in the `/shell` subdirectory.

The install scripts installs the ``elvis`` script into `/opt/dims/bin`, and all the required jars into `/opt/dims/jars/tupelo`.

Once installed, the shell can be started via the ``elvis`` command, assuming `/opt/dims/bin` is on your path.

-------------------
Running the Shell
-------------------

The shell is started via the ``elvis`` command.

The ``elvis`` script will only run if installed under `/opt/dims` or if all the required jars are under a `target/` folder in the current directory.
If you need to run it from another directory, you will need to modify the script - it's just a bash script that sets the classpath and starts Java.

By default, the shell uses a local store in `./test-store` - if this directory does not exist it will give you an error. The default store location is configurable, see :ref:`shell-config`.
Use the `-s <PATH/URL>` option to specify a different store location. To see all possible flags, use the `-h` flag.

Once the shell is running, it will display the following::

 scott@ux32vd:~/tupelo/shell$ ./elvis -s http://localhost:8080/
 ----
 WARNING: Server version is different than client
 Client version: 1.2.1-SNAPSHOT
 Server version: 1.2.0
 ----
 Store: http://localhost:8080/
 Located /dev/sda
 Located /dev/sdb
 tupelo>

Here, you can see that the shell is using `http://localhost:8080/` as the store, and it is verifying that the versions match.
It prints the store location and disks it finds. The last line is the shell waiting for commands.

To see a list and description of all commands available, run `h`. To exit the shell, run `exit`.

-----------------
Detecting Drives
-----------------

The shell has an internally hard-coded list of drives to check for, essentially `/dev/sd[a-i]`.
To manually add a drive or image, use the `-u` flag.

**NOTE:** In order to access the drives directly under `/dev`, the user running the shell must be in the `disks` group, or the shell must
be run with elevated privileges.

.. _shell-config:

----------------------------
Shell Configuration Options
----------------------------

The configuration options available for the shell are:

===============  ==========================  =============
 Option          Description                 Default Value
===============  ==========================  =============
store-location   The default store location  ./test-store
===============  ==========================  =============

*See* :ref:`setting-config` *for where to change these options.*

*****************
Tupelo Web Store
*****************

The Tupelo web store is packaged into a WAR (Web ARchive) file.
A WAR file can be run in various Java Servlet Containers, such as `Jetty <http://www.eclipse.org/jetty/>`_ and `Tomcat <http://tomcat.apache.org/>`_.
The Tupelo Web Store has been tested in both a Jetty and Tomcat 7 server.

-------------------
Running the Server
-------------------

^^^^^^^
Jetty
^^^^^^^

The fastest way to get the web store up and running is to run ``mvn package -Prun-server`` in the root of the project.
This will compile the whole project and start the server on ports 8888 and 8889. Access it via http://localhost:8888/

^^^^^^^
Tomcat
^^^^^^^

For longer running uses (and in the Tupelo server Docker conatiner), a server such as Tomcat is recommended.
To deploy on Tomcat, you just need to build the project and copy the WAR file from under `http/server/target/`
into the Tomcat server's `webapps` directory and start Tomcat.
The store will be available at http://localhost:8080/tupelo by default under Tomcat.

------------------------
Web Store Configuration
------------------------

The configuration options for the we store are:

==========  ======================================================  ==============
Option      Description                                             Default Value
==========  ======================================================  ==============
dataroot    The path to the underlying filesystem store             $HOME/.tupelo
amqp.url    The AMQP url to connect to (Including authentication)   ${amqp.url}
==========  ======================================================  ==============

*See* :ref:`setting-config` *for where to change these options.*

.. _setting-config:

******************************
Setting Configuration Options
******************************

The different ways to define these options are:

1: Specified as JVM arguments, prefixed with tupelo.* (Example: -Dtupelo.store=./test-store)

2: In a real file name $(HOME)/.tupelo

3: In a real file name /etc/tupelo.prp

4: In a resource (classpath-based) named /tupelo.prp

The first match wins.
