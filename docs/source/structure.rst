=============================
Project Structure
=============================

*********************
Maven Structure
*********************

Due to Maven's module nature, the project is broken down into
many sub-projects.
A summary of the current sub-projects and their path in the repo::

 Tupelo Disk Management - Parent ............................. /
 Tupelo Disk Management - Utilities .......................... /utils
 Tupelo Disk Management - Model (Parent) ..................... /model
 Tupelo Disk Management - Model (Core) ....................... /model/core
 Tupelo Disk Management - Model (Physical Disks) ............. /model/physical
 Tupelo Disk Management - Model (Virtual Disks) .............. /model/virtual
 Tupelo Disk Management - Logging Infrastructure ............. /logging
 Tupelo Disk Management - Store (Parent) ..................... /store
 Tupelo Disk Management - Store API .......................... /store/api
 Tupelo Disk Management - FileSystem Store ................... /store/filesys
 Tupelo Disk Management - Null/Test Store .................... /store/null
 Tupelo Disk Management - Mountable FileSystem via Fuse ...... /fuse
 Tupelo Disk Management - Managed Disk Processing Tools ...... /store/tools
 Tupelo Disk Management - Http (Parent) ...................... /http
 Tupelo Disk Management - Http Store Proxy ................... /http/client
 Tupelo Disk Management - AMQP Parent ........................ /amqp
 Tupelo Disk Management - AMQP Objects ....................... /amqp/objects
 Tupelo Disk Management - AMQP Replying Programs ............. /amqp/server
 Tupelo Disk Management - Http Store Servlet ................. /http/server
 Tupelo Disk Management - AMQP Requesting Programs ........... /amqp/client
 Tupelo Disk Management - Shell .............................. /shell
 Tupelo Disk Management - Command Line Utilities (DEPRICATED)  /cli

****************
Store Structure
****************

The core of Tupelo is the store.

The Store API is defined by an interface in the `/store/api` sub-project.

There are 3 implementations of the Store API in the code base:

* `NullStore` - Does nothing. As interesting as it sounds.

  * Found in `/store/null`

* `HttpStoreProxy` - Proxies the store API to the HTTP server.

  * Heavily linked to the HTTP servlet endpoints defined in `/http/server`
  * Found in `/http/client`

* `FilesystemStore` - The main store class. This store is used by the HTTP server to store the data.

  * Found in `/store/filesys`

****************
FilesystemStore
****************

The main store is the FilesystemStore.
This store saves everything in folder structure::

 .
 ├── disks
 │   └── Lexar-JD_Lightning_II-
 │       └── 20151009.0002
 │           ├── attrs
 │           │   ├── unmanaged.inetaddress
 │           │   ├── unmanaged.path
 │           │   ├── unmanaged.timestamp
 │           │   └── unmanaged.user
 │           ├── data
 │           │   └── Lexar-JD_Lightning_II--20151009.0002.tmd
 │           └── fileRecord.sqlite
 ├── session.txt
 ├── temp
 └── uuid.txt

A quick summary of the structure:

* `disks` - All the disk data is stored under here

 * Each disk will have a `<Disk Name>/<Session>` directory
 * A disk can have multiple session directories, one for each time an image is uploaded
 * Under the session directory, there are directories for the disk image its self, and a folder for all the attributes

  * Attributes are a simple key/value store
  * You can store anything as an attribute, and the key is it's file name

 * Finally, there is a fileRecord.sqlite file

  * This contains the processed file record information from the disk
  * When you search for a specific file hash (Via the shell or AMQP), it uses this database
  * SQLite is used because it is self contained, efficient, and multi-process safe

