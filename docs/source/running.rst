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

By default, the shell uses a local store in `./test-store` - if this directory does not exist it will give you an error.
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

*****************
Tupelo Web Store
*****************



