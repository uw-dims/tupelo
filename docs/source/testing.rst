========
Testing
========

This section has information on how to do the tests to satisfy the :ref:`dimstp:dimstestplan`.

*************************
Disk Acquisition Testing
*************************

-------------------------
Initial Acquisition Test
-------------------------

*This is in reference to 4.3.3.3.1 under* :ref:`dimstp:diutconditions`

**Prerequisites:**

Host machine with the Tupelo shell installed and multiple disks, and the Tupelo web store running on another system on the same network.

If no machine with multiple drives is available, taking images of external or flash drives will also work.

**Procedure:**

To test the initial acquisition of a disk follow the procedure from :ref:`boot-test1`, starting at step #3.

**NOTE:** Make sure to not take an image of the running system's root disk (Whatever is mounted at ``/``) - the files
on the root drive may change while creating the image, causing issues. Take an image of a disk that is not in use.

-------------------------
Repeat Acquisition Test
-------------------------



***********************
Bootable Media Testing
***********************

.. _boot-test1:

----------------------
Bootable Media Test 1
----------------------

*This is in reference to 4.3.3.3.7 under* :ref:`dimstp:diutconditions`

**Prerequisites:**

Host machine capable of booting from a USB, an USB with a live Linux environment with the
Tupelo shell (See :ref:`create-live` for how to modify a Live ISO),
and the Tupelo web store running on another system on the same network.

**Procedure:**

1. Boot the USB with the Tupelo shell installed.
2. Connect the live system to the network, if it does not automatically connect.
3. Start the Tupelo shell, and tell it to connect to the Web Store you have running.

  * Use the `-s` option to specify the store location, such as ``elvis -s http://tupelo-store:8080/``

4. Make sure that the shell locates the system's hard drives.

  * The output of starting the shell should be similar to the following::

     caine@live:~$ elvis -s http://tupelo-store:8080/
     Store: http://tupelo-store:8080/
     Located /dev/sda
     Located /dev/sdb
     Located /dev/sdc
     tupelo>

 * If there are no `Located /dev/sd*` lines, or if there is an exception verifying the web store's version, then stop.

5. Send a hard drive from the host machine to the store via ``putdisk``

 * Usually `/dev/sda` if the main drive of a system, so send it::

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

Host machine capable of booting from a USB, an USB with a live Linux environment with the
Tupelo shell (See :ref:`create-live` for how to modify a Live ISO),
and an external hard drive that is larger than the drive in the host machine.

**Procedure:**

1. Boot the USB with the Tupelo shell installed.
2. Plug in and mount the external hard drive, if it is not automatically mounted.
3. Get the mount point of the external drive. You can check the output of the ``mount`` command to find it.
4. Make a new directory on the external drive to use as the store root
5. Start the Tupelo shell, and tell it to use the store root you just created.

  * Use the `-s` option to specify the store location, such as ``elvis -s /mnt/external/store``

5. Make sure that the shell locates the system's hard drives.

  * The output of starting the shell should be similar to the following::

     caine@live:~$ elvis -s /mnt/external/store
     Store: /mnt/external/store
     Located /dev/sda
     Located /dev/sdb
     Located /dev/sdc
     tupelo>

 * If there are no `Located /dev/sd*` lines, stop.

6. Send a hard drive from the host machine to the store via ``putdisk``

 * Usually `/dev/sda` if the main drive of a system, so send it::

    tupelo> putdisk /dev/sda

 * Depending on the size of the drive and the network, this can take a long time.
 * This will print progress as it runs.

7. Verify that the disk is now a server-managed disk via ``ms``

 * This will list all the disks currently managed via the server. Example output::

    tupelo> ms
    N                                         ID           Session
    1          ATA-SanDisk_SSD_i100-122500153376     20151030.0001

 * The ID of the disk is build from the manufacturer information and serial number of the drive

8. Done.
