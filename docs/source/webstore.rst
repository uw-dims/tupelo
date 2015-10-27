======================
Web Store Information
======================

Some general information on how and what the web store does while running.

*********************
Background Processes
*********************

When the web store starts, it starts some background threads to handle various events.
All these threads are started and stopped in the `ContextListener` class.

----------------------
AMQP Listening Thread
----------------------

The server starts a thread that runs the `FileHashService` class from the `amqp/server/` subproject in its own thread.
This thread listens for the 'who-has' MD5 hash requests and responds to them.

This thread will crash and print a stack trace if there is an exception, but it should not stop the whole server.
There will be no attempts to re-create the thread once it crashes - so it is possible that the store may be running but
may stop responding to AMQP requests.

--------------------------
Temp File Cleaning Thread
--------------------------

The server starts a thread which checks the temp folder every 5 minutes, and will delete files that haven't been modified in 10 minutes.
This is started via the `TempDirCleaner` class in the `store/filesystem/` subproject.

This thread was added because temp files would build up if the client is stopped or crashes while sending a disk image.

.. _filerecord-check:

-------------------------
File Record Check Thread
-------------------------

This is a thread, controlled via the `DiskFileRecordService` class in the `http/server/` subproject that checks for managed disks without file records.
This thread checks every 20 minutes for disks without file records, and will process disks in the background one at a time.

**NOTE:** This could cause issues in a clustered environment - If multiple servers process the same disk, they could generate duplicate records.
Before saving the records, they double check that there are none - but it could happen.

***********
MDFS Mount
***********

Because of the :ref:`filerecord-check`, the web store creates a FUSE mount point at `/mdfs` in the underlying filesystem store.

If the server is shut down imporperly, it is possible that this could be left mounted. If you have issues starting the server,
run ``mount`` and check for something like the following::

 javafs on /mnt/arch/tupelo-store/mdfs type fuse.javafs (ro,nosuid,nodev,user=scott)

If you find something like this, run ``sudo umount /path/to/mount/point/`` and it will be cleaned up.

