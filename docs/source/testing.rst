========
Testing
========

This section has information on how to do the tests to satisfy the :ref:`dimstp:dimstestplan`.

**NOTE:** Make sure to not take an image of a running system's root disk (Whatever is mounted at ``/``) - the files
on the root drive may change while creating the image, causing issues. Take an image of a disk that is not in use.

************
Definitions
************

.. _test-client:

1. **Client Host Machine**

  * A Ubuntu 14.04 based system with the Tupelo shell installed. See :ref:`install-shell` for more information.

.. _test-web-store:

2. **Tupelo Web Store**

  * A Ubuntu 14.04 based system running the Tupelo Web store. See :ref:`tupelo-web-store` for more information.
  * You will need to know the IP address or hostname to connect from the client
  * The client and web need to be on the same network, and able to communicate with each other over HTTP on port 8080 (By default)

    * A fast (At least 1Gbps) connection is recommended, especially for transferring large images.

.. _test-disk:

3. **Disk**

  * A disk, or disk image, for the Tupelo shell to analyze or add to a store.

    * Physical disks will be auto-detected by the shell
    * External or flash drives can be used. Plug them in before starting the shell, and they should be auto-detected
    * Disk images need to be specified with ``-u <path/to/image>``

  * Before analyzing or acquiring a disk or image, it should either be unmounted or read-only.

    * If the disk or image changes during acquisition, it could corrupt the managed image.

.. _test-live-env:

4. **Live Environment**

  * A bootable USB or CD with the Tupelo Shell preinstalled.
  * See :ref:`create-live` for instructions for creating a live ISO.

*************************
Disk Acquisition Testing
*************************

.. _initial-test:

-------------------------
Initial Acquisition Test
-------------------------

*This is in reference to 4.3.3.3.1 under* :ref:`dimstp:diutconditions`

**Prerequisites:**

1. A :ref:`Client Machine <test-client>` with an available :ref:`disk <test-disk>`.

2. A running :ref:`Web Store <test-web-store>`

**Procedure:**

To test the initial acquisition of a disk follow the procedure from :ref:`boot-test1`, starting at step #3.

-------------------------
Repeat Acquisition Test
-------------------------

Follow the :ref:`initial-test` procedure, making sure to use the same :ref:`Client Machine <test-client>` and :ref:`disk <test-disk>` as the first time.

***********************
Bootable Media Testing
***********************

.. _boot-test1:

----------------------
Bootable Media Test 1
----------------------

*This is in reference to 4.3.3.3.7 under* :ref:`dimstp:diutconditions`

**Prerequisites:**

1. A :ref:`Live Environment <test-live-env>` and a computer that can boot it

2. A running :ref:`Web Store <test-web-store>`

**Procedure:**

1. Boot live environment.
2. Connect to the network if it does not automatically do so.
3. Open a terminal, and start the Tupelo shell while specifying the Web Store you have running.

  * Use the `-s` option to specify the store location, such as ``elvis -s http://tupelo-store:8080/``

4. Make sure that the shell locates the system's hard drives, and that the store is correct.

  * The output of starting the shell should be similar to the following::

     caine@live:~$ elvis -s http://tupelo-store:8080/
     Store: http://tupelo-store:8080/
     Located /dev/sda
     Located /dev/sdb
     Located /dev/sdc
     tupelo>

 * If there are no `Located /dev/sd*` lines or if there are exception messages, then stop.

5. Send a hard drive from the host machine to the store via ``putdisk``

 * Usually `/dev/sda` is the primary drive of a system - pick a drive that is not in use and send it::

    tupelo> putdisk /dev/sda

 * Depending on the size of the drive and the network, this can take a long time.
 * This will print progress as it runs.

6. Verify that the disk is now a server-managed disk via ``ms``

 * This will list all the disks currently managed via the server. Example output::

    tupelo> ms
    N                                         ID           Session
    1          ATA-SanDisk_SSD_i100-122500153376     20151030.0001

 * The ID of the disk is build from the manufacturer information and serial number of the drive

7. Done.

----------------------
Bootable Media Test 2
----------------------

*This is in reference to 4.3.3.3.8 under* :ref:`dimstp:diutconditions`

**Prerequisites:**

1. A :ref:`Live Environment <test-live-env>` and a computer that can boot it

2. A external hard drive or flash drive larger than the computer's drive(s).

**Procedure:**

1. Boot live environment.
2. Plug in and mount the external hard drive, if it is not automatically mounted.
3. Get the mount point of the external drive.

  * You can check the output of the ``mount`` command to find it.

4. Make a new directory on the external drive to use as the store root
5. Start the Tupelo shell while specifying the store root you just created.

  * Use the `-s` option to specify the store location, such as ``elvis -s /mnt/external/store``

5. Make sure that the shell locates the system's hard drives.

  * The output of starting the shell should be similar to the following::

     caine@live:~$ elvis -s /mnt/external/store
     Store: /mnt/external/store
     Located /dev/sda
     Located /dev/sdb
     Located /dev/sdc
     tupelo>

  * If there are no `Located /dev/sd*` lines or if there are exception messages, then stop.

6. Send a hard drive from the host machine to the store via ``putdisk``

 * Usually `/dev/sda` if the main drive of a system - pick a drive not in use and send it::

    tupelo> putdisk /dev/sda

 * Depending on the size of the drive, this can take a long time.
 * This will print progress as it runs.

7. Verify that the disk is now a server-managed disk via ``ms``

 * This will list all the disks currently managed via the server. Example output::

    tupelo> ms
    N                                         ID           Session
    1          ATA-SanDisk_SSD_i100-122500153376     20151030.0001

 * The ID of the disk is build from the manufacturer information and serial number of the drive

8. Done.
